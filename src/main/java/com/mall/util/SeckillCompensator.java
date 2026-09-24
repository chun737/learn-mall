package com.mall.util;

import com.mall.common.Constants;
import com.mall.dto.SeckillOrderMessage;
import com.mall.mapper.OrderMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static com.mall.common.Constants.SECKILL_COMPENSATE_PENDING_KEY;

/**
 * 秒杀失败补偿的统一入口（审计 严重-1 / 高-1 / 高-6）。
 *
 * <h3>为什么补偿必须收敛到这里</h3>
 * 补偿动作是"Redis 库存回补 + 已购额度回退 + 结果标 FAILED"。旧实现三处入口
 * （消费者失败兜底、生产端发送失败回调、死信无人管）全是分步写入——
 * RocketMQ 是 at-least-once，消息重投 / 进死信后重复补偿会把库存越补越多（超卖），
 * 已购额度补成负数后限购判断（bought + buy &gt; perLimit）被负基数放行（限购失效）。
 *
 * <h3>三道防线</h3>
 * <ol>
 *   <li><b>幂等闸门</b>：由回补 Lua 与库存、已购、结果写入一起 SETNX；同一订单
 *       全集群只有一次回补机会，客户端超时重试也不会重复回补</li>
 *   <li><b>DB 复核</b>：回补前查 order 表——订单已存在（发送超时误判 / 消费端已建成单）
 *       说明这笔"失败"是假的，库存已由支付/取消路径正确记账，绝不能再补</li>
 *   <li><b>原子回补</b>：库存、限购额度和失败结果由同一段 Lua 一次完成；三处 key 使用同一活动 hash tag，
 *       Redis Cluster 下也不会出现跨 slot 的部分成功</li>
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

    /**
     * 原子回补：不创建已被活动终止流程删除的库存 key，不把已购数量减成负数，
     * 并把失败状态写成 JSON 字符串以兼容 RedisTemplate 的 GenericJackson 序列化器。
     */
    private static final DefaultRedisScript<Long> COMPENSATE_SCRIPT;

    static {
        COMPENSATE_SCRIPT = new DefaultRedisScript<>();
        COMPENSATE_SCRIPT.setScriptText("""
                local qty = tonumber(ARGV[1])
                local userId = ARGV[2]
                local fallbackTtl = tonumber(ARGV[3])
                local gateTtl = tonumber(ARGV[4])
                local failedValue = ARGV[5]

                if qty == nil or qty <= 0 or userId == nil or userId == '' or failedValue == nil or failedValue == ''
                        or fallbackTtl == nil or fallbackTtl <= 0 or gateTtl == nil or gateTtl <= 0 then
                    return -1
                end

                -- 闸门和三处业务 key 使用同一 hash tag，SETNX 与回补状态同一事务完成。
                if redis.call('SETNX', KEYS[4], '1') == 0 then
                    return 0
                end
                if gateTtl ~= nil and gateTtl > 0 then
                    redis.call('EXPIRE', KEYS[4], gateTtl)
                end

                -- 活动已终止并删除库存 key 时不重新创建，避免历史订单取消复活活动库存。
                local stockExists = redis.call('EXISTS', KEYS[1]) == 1
                if stockExists then
                    redis.call('INCRBY', KEYS[1], qty)
                    if redis.call('TTL', KEYS[1]) < 0 and fallbackTtl ~= nil and fallbackTtl > 0 then
                        redis.call('EXPIRE', KEYS[1], fallbackTtl)
                    end
                end

                -- 已购字段不存在时不写负数；回退到 0 时直接删除字段。
                local bought = tonumber(redis.call('HGET', KEYS[2], userId)) or 0
                if bought <= qty then
                    redis.call('HDEL', KEYS[2], userId)
                else
                    redis.call('HINCRBY', KEYS[2], userId, -qty)
                end
                if redis.call('EXISTS', KEYS[2]) == 1 and redis.call('TTL', KEYS[2]) < 0 then
                    redis.call('EXPIRE', KEYS[2], fallbackTtl)
                end

                redis.call('HSET', KEYS[3], userId, failedValue)
                if redis.call('TTL', KEYS[3]) < 0 then
                    redis.call('EXPIRE', KEYS[3], fallbackTtl)
                end
                -- 2 表示业务回补已完成，但库存 key 不存在，供 Java 侧记录告警。
                if stockExists then
                    return 1
                end
                return 2
                """);
        COMPENSATE_SCRIPT.setResultType(Long.class);
    }

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
     * 立即补偿（消费端建单失败 / 死信 / 同步发送失败）：DB 复核 → Redis 原子闸门与回补。
     *
     * @return true=本次执行了回补；false=被闸门或 DB 复核拦下（无需补偿）
     * @throws RuntimeException Redis 回补失败；Lua 未成功返回时不会完成业务回补，调用方可重投重试
     */
    public boolean compensateNow(SeckillOrderMessage msg) {
        if (orderExists(msg.getOrderNo())) {
            log.info("秒杀补偿 DB 复核：订单已存在，跳过回补 orderNo={}", msg.getOrderNo());
            return false;
        }
        return restore(msg);
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
                // 补偿失败（Redis 抖动等）：条目保留在 ZSET，下一轮自然重试；
                // Lua 成功执行过时闸门会保留，重试只会返回幂等结果。
                log.error("秒杀待补偿条目处理失败，下轮重试: {}", member, e);
            }
        }
        return handled;
    }

    /** 订单是否已存在：显式包含逻辑删除行，避免已建单消息被误补库存。 */
    private boolean orderExists(String orderNo) {
        return orderMapper.countByOrderNoIncludingDeleted(orderNo) > 0;
    }

    /** Redis 回补：库存、限购额度和失败结果在一次 Lua 执行中完成 */
    private boolean restore(SeckillOrderMessage msg) {
        String stockKey = Constants.seckillStockKey(msg.getActivityId());
        String boughtKey = Constants.seckillBoughtKey(msg.getActivityId());
        String resultKey = Constants.seckillResultKey(msg.getActivityId());
        String gateKey = Constants.seckillCompensatedKey(msg.getActivityId(), msg.getOrderNo());
        Long result = redisTemplate.execute(
                COMPENSATE_SCRIPT,
                RedisSerializer.string(),
                null,
                List.of(stockKey, boughtKey, resultKey, gateKey),
                String.valueOf(msg.getQuantity()),
                String.valueOf(msg.getUserId()),
                String.valueOf(Constants.SECKILL_KEY_TTL_FALLBACK.getSeconds()),
                String.valueOf(GATE_TTL.getSeconds()),
                Constants.SECKILL_RESULT_FAILED_JSON);
        if (result == null || result == -1) {
            throw new IllegalStateException("秒杀补偿脚本执行失败：参数非法或 Redis 未返回结果");
        }
        if (result == 2) {
            log.warn("秒杀补偿已完成，但库存 key 不存在，未重新创建：activityId={}, orderNo={}",
                    msg.getActivityId(), msg.getOrderNo());
        }
        return result == 1 || result == 2;
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
