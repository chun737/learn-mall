package com.mall.util;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mall.common.Constants;
import com.mall.dto.SeckillOrderMessage;
import com.mall.entity.Order;
import com.mall.mapper.OrderMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;

import static com.mall.common.Constants.SECKILL_COMPENSATE_PENDING_KEY;
import static com.mall.common.Constants.SECKILL_COMPENSATED_PREFIX;
import static com.mall.common.Constants.SECKILL_RESULT_PREFIX;

/**
 * 秒杀失败补偿的统一入口（审计 严重-1 / 高-1 / 高-6）。
 *
 * <h3>为什么补偿必须收敛到这里</h3>
 * 补偿动作是"Redis 库存 INCR + 已购额度 HINCRBY -qty + 结果标 FAILED"。旧实现三处入口
 * （消费者失败兜底、生产端发送失败回调、死信无人管）全是<b>无条件 INCRBY</b>——
 * RocketMQ 是 at-least-once，消息重投 / 进死信后重复补偿会把库存越补越多（超卖），
 * 已购额度补成负数后限购判断（bought + buy &gt; perLimit）被负基数放行（限购失效）。
 *
 * <h3>两道防线</h3>
 * <ol>
 *   <li><b>幂等闸门</b>：SETNX {@code mall:seckill:compensated:{orderNo}}，同一订单
 *       全集群只有一次回补机会；Redis 侧执行失败时释放闸门，让消息重投后还能重试</li>
 *   <li><b>DB 复核</b>：回补前查 order 表——订单已存在（发送超时误判 / 消费端已建成单）
 *       说明这笔"失败"是假的，库存已由支付/取消路径正确记账，绝不能再补</li>
 * </ol>
 *
 * <h3>发送失败为何走延迟队列</h3>
 * asyncSend 的 onException 在"客户端发送超时"时也会触发，此刻消息可能仍在消费者
 * 事务中尚未提交——立即查 DB 会误判"订单不存在"而错误回补。因此先写进
 * ZSET 待补偿队列（score = now + 延迟），由 {@code SeckillCompensationTask} 到期后
 * 以 DB 为准绳复核再补偿；延迟远大于消费事务的毫秒级耗时。
 */
@Component
public class SeckillCompensator {

    private static final Logger log = LoggerFactory.getLogger(SeckillCompensator.class);

    /** 待补偿延迟：覆盖"消费端事务提交中"的窗口 */
    private static final long DELAY_MS = 15_000;
    /** 幂等闸门 TTL：需覆盖活动窗口 + 消息重投周期；过期后补偿本身也已无意义 */
    private static final Duration GATE_TTL = Duration.ofHours(24);

    private final RedisTemplate<String, Object> redisTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    private final OrderMapper orderMapper;

    public SeckillCompensator(RedisTemplate<String, Object> redisTemplate,
                              StringRedisTemplate stringRedisTemplate,
                              OrderMapper orderMapper) {
        this.redisTemplate = redisTemplate;
        this.stringRedisTemplate = stringRedisTemplate;
        this.orderMapper = orderMapper;
    }

    /**
     * 立即补偿（消费端建单失败 / 死信 / 同步发送失败）：幂等闸门 → DB 复核 → Redis 回补。
     *
     * @return true=本次执行了回补；false=被闸门或 DB 复核拦下（无需补偿）
     * @throws RuntimeException Redis 回补失败（闸门已释放，调用方可重投重试）
     */
    public boolean compensateNow(SeckillOrderMessage msg) {
        String gateKey = SECKILL_COMPENSATED_PREFIX + msg.getOrderNo();
        Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(gateKey, "1", GATE_TTL);
        if (!Boolean.TRUE.equals(acquired)) {
            return false;   // 已被其他入口补偿过（重投/死信/延迟任务撞车），闸门拦下
        }
        try {
            if (orderExists(msg.getOrderNo())) {
                log.info("秒杀补偿 DB 复核：订单已存在，跳过回补 orderNo={}", msg.getOrderNo());
                return false;
            }
            restore(msg);
            return true;
        } catch (RuntimeException e) {
            // 释放闸门：让消息重投 / 下一轮任务还能重试，否则这次失败会把补偿永久弄丢
            stringRedisTemplate.delete(gateKey);
            throw e;
        }
    }

    /**
     * 延迟补偿（生产端 asyncSend 的 onException）：入 ZSET 待补偿队列，
     * 到期由定时任务以 DB 为准绳复核后真正回补。
     * 同一 orderNo 重复入队只会刷新 score，天然幂等。
     */
    public void scheduleCompensation(SeckillOrderMessage msg) {
        stringRedisTemplate.opsForZSet().add(
                SECKILL_COMPENSATE_PENDING_KEY, encode(msg), System.currentTimeMillis() + DELAY_MS);
        log.warn("秒杀发送失败已入待补偿队列（{}ms 后 DB 复核）: orderNo={}", DELAY_MS, msg.getOrderNo());
    }

    /**
     * 处理到期条目（定时任务调用）：DB 复核 → 补偿 → 出队。
     *
     * @return 本轮完成补偿/复核的条数
     */
    public int processPendingCompensations() {
        Set<String> due = stringRedisTemplate.opsForZSet()
                .rangeByScore(SECKILL_COMPENSATE_PENDING_KEY, 0, System.currentTimeMillis());
        if (due == null || due.isEmpty()) {
            return 0;
        }
        int handled = 0;
        for (String member : due) {
            SeckillOrderMessage msg;
            try {
                msg = decode(member);
            } catch (Exception e) {
                // 格式非法的条目永远补不了，出队丢弃，避免死循环占用
                log.error("待补偿条目格式非法，已丢弃: {}", member, e);
                stringRedisTemplate.opsForZSet().remove(SECKILL_COMPENSATE_PENDING_KEY, member);
                continue;
            }
            try {
                compensateNow(msg);
                stringRedisTemplate.opsForZSet().remove(SECKILL_COMPENSATE_PENDING_KEY, member);
                handled++;
            } catch (Exception e) {
                // 补偿失败（Redis 抖动等）：条目保留在 ZSET，下一轮自然重试（闸门已在内部释放）
                log.error("秒杀待补偿条目处理失败，下轮重试: {}", member, e);
            }
        }
        return handled;
    }

    /** 订单是否已存在。@TableLogic 自动过滤已删除行：被删的秒杀单视为不存在，回补是合理的 */
    private boolean orderExists(String orderNo) {
        Long count = orderMapper.selectCount(new QueryWrapper<Order>().eq("order_no", orderNo));
        return count != null && count > 0;
    }

    /** Redis 回补：与消费者/生产端旧逻辑同 key 同模板，口径不变 */
    private void restore(SeckillOrderMessage msg) {
        String stockKey = Constants.seckillStockKey(msg.getActivityId());
        redisTemplate.opsForValue().increment(stockKey, msg.getQuantity());
        // INCRBY 对不存在的 key 会创建一个永久 key：顺手补 TTL，防止秒杀库存 key 永久滞留
        Constants.refreshSeckillKeyTtl(redisTemplate, stockKey);
        redisTemplate.opsForHash().increment(
                Constants.seckillBoughtKey(msg.getActivityId()), msg.getUserId(), -msg.getQuantity());
        redisTemplate.opsForHash().put(
                SECKILL_RESULT_PREFIX + msg.getActivityId(), msg.getUserId().toString(), "FAILED");
    }

    /** 待补偿条目编码：全部是数字/订单号，用 | 拼接即可，避免走对象序列化器 */
    private String encode(SeckillOrderMessage msg) {
        return msg.getActivityId() + "|" + msg.getUserId() + "|" + msg.getQuantity() + "|" + msg.getOrderNo();
    }

    private SeckillOrderMessage decode(String member) {
        String[] parts = member.split("\\|");
        if (parts.length != 4) {
            throw new IllegalArgumentException("expect 4 fields: " + member);
        }
        SeckillOrderMessage msg = new SeckillOrderMessage();
        msg.setActivityId(Long.valueOf(parts[0]));
        msg.setUserId(Long.valueOf(parts[1]));
        msg.setQuantity(Integer.valueOf(parts[2]));
        msg.setOrderNo(parts[3]);
        return msg;
    }
}
