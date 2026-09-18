package com.mall.util;

import com.mall.common.Constants;
import com.mall.entity.Order;
import com.mall.entity.OrderItem;
import com.mall.entity.SeckillActivity;
import com.mall.mapper.SeckillActivityMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 秒杀库存回补组件：订单作废（取消 / 超时 / 全额退款）时，把占用掉的秒杀库存还回去。
 *
 * <p>为什么单独抽一个组件、而不是挂在 ISeckillActivityService 上：
 * <ul>
 *   <li>SeckillActivityServiceImpl 的构造器已注入 IOrderService，若 OrderServiceImpl 反向注入
 *       ISeckillActivityService 会构成循环依赖，Spring Boot 默认禁止循环引用 → 启动直接失败；</li>
 *   <li>回补有两个调用方（OrderServiceImpl 的取消/超时、PaymentServiceImpl 的退款），
 *       抽成组件才能让"机制只有一份实现"，避免两处各写一遍而口径漂移。</li>
 * </ul>
 *
 * <p>回补要同时处理两处状态，且两处都必须是"错就跳过、只告警"的弱语义：
 * <ul>
 *   <li><b>DB</b>：seckill_activity.available_stock 行内自增（LEAST 封顶在 total_stock）；</li>
 *   <li><b>Redis</b>：seckill:{act:id}:stock 加回、seckill:{act:id}:bought 回退限购额度。</li>
 * </ul>
 * 之所以全程不抛异常：调用方（取消订单）的 DB 事务已经改完状态了，此时因为 Redis 抖动
 * 抛出去只会让用户看到一个"取消失败"，而库里其实已经取消——宁可少回补一次库存（可由对账发现），
 * 也不能让取消动作对用户表现为失败。
 *
 * <p><b>识别依据</b>：只看 {@code order.orderSource == ORDER_SOURCE_SECKILL}。
 * 不加这个标记的话，任何「SKU 恰好挂着秒杀活动、下单时间又落在活动时间窗内」的普通订单，
 * 在取消时都会被当成秒杀单白回补一次秒杀库存 → 秒杀库存虚增 → 超卖。
 *
 * @author 乐乐
 */
@Component
public class SeckillStockRestorer {

    private static final Logger log = LoggerFactory.getLogger(SeckillStockRestorer.class);

    private final RedisTemplate<String, Object> redisTemplate;
    private final SeckillActivityMapper seckillActivityMapper;

    public SeckillStockRestorer(RedisTemplate<String, Object> redisTemplate,
                                SeckillActivityMapper seckillActivityMapper) {
        this.redisTemplate = redisTemplate;
        this.seckillActivityMapper = seckillActivityMapper;
    }

    /**
     * 按订单回补秒杀库存（普通订单直接返回，零开销）。
     *
     * @param order 订单实体（需带 orderSource / createdAt；调用方用 selectById/selectOne 查出来的就满足）
     * @param items 该订单的明细列表（回补粒度是「SKU × 数量」，故必须逐条处理）
     */
    public void restoreForSeckillOrder(Order order, List<OrderItem> items) {
        // 1. 来源闸门：非秒杀单直接返回。这是防"误回补"的唯一开关，必须在最前面
        if (order == null || order.getOrderSource() == null
                || order.getOrderSource() != Constants.ORDER_SOURCE_SECKILL) {
            return;
        }
        if (items == null || items.isEmpty() || order.getCreatedAt() == null) {
            // createdAt 为空就没法做时间窗反查（订单表该列 NOT NULL，这里只是防御脏数据）
            log.warn("秒杀订单回补跳过：明细为空或下单时间为空，orderNo={}", order.getOrderNo());
            return;
        }

        // 涉及到的活动：用方法内局部集合收集，循环结束后统一补 TTL。
        // 注意不能用实例字段——本类是单例，被取消/超时/退款多条路径并发调用，实例字段会互相覆盖。
        Set<Long> touchedActivityIds = new HashSet<>();

        for (OrderItem item : items) {
            if (item.getSkuId() == null || item.getQuantity() == null || item.getQuantity() <= 0) {
                continue;
            }
            int qty = item.getQuantity();
            // 2. 反查活动：订单表没有 activity_id 列，用「SKU + 下单时刻落在活动时间窗内」定位。
            //    查不到说明这单不是在活动期内产生的（例如活动被删），不属于秒杀占用，跳过
            Long activityId;
            try {
                activityId = seckillActivityMapper.selectActivityIdBySkuAt(item.getSkuId(), order.getCreatedAt());
            } catch (Exception e) {
                log.warn("秒杀订单回补失败：反查活动异常，orderNo={}, skuId={}", order.getOrderNo(), item.getSkuId(), e);
                continue;
            }
            if (activityId == null) {
                log.warn("秒杀订单回补跳过：未在活动时间窗内找到该 SKU 的活动（DB 库存不动），orderNo={}, skuId={}",
                        order.getOrderNo(), item.getSkuId());
                continue;
            }

            // 收集涉及的活动，循环结束后统一补 TTL（见下方 ensureKeyTtl）
            touchedActivityIds.add(activityId);

            // 3. DB 回补：LEAST 封顶，影响行数 0 表示活动已逻辑删除
            try {
                int rows = seckillActivityMapper.restoreAvailableStock(activityId, qty);
                if (rows == 0) {
                    log.warn("秒杀订单回补 DB 影响 0 行（活动可能已删除）：activityId={}, orderNo={}, qty={}",
                            activityId, order.getOrderNo(), qty);
                }
            } catch (Exception e) {
                log.warn("秒杀订单回补 DB 失败：activityId={}, orderNo={}, qty={}", activityId, order.getOrderNo(), qty, e);
            }

            // 4. Redis 回补：库存计数 + 限购额度
            restoreRedisStock(activityId, qty, order.getOrderNo());
            restoreRedisBought(activityId, order.getUserId(), qty, order.getOrderNo());
        }

        // 5. 统一补 TTL：给"原本就存在但 TTL 缺失"的 key 兜底补上过期时间，
        //    同时也兜住 restoreRedisStock 里"判断存在后、写入前恰好过期"的极窄竞态
        //    （若不在此时刚好过期，那一步的 increment 会用一个几乎等于剩余 TTL 的短 TTL 重建 key，
        //      不会产生永久 key；这里只是让过期时间回归正常口径）。
        //    与回补动作分开、放在最后：本方法任何一步失败都只告警不抛，不影响回补结果。
        touchedActivityIds.forEach(this::ensureKeyTtl);
    }

    /**
     * 给某活动的 stock / bought 两个 key 补上「活动结束时间 + 缓冲」的过期时间。
     *
     * <p><b>为什么需要</b>：Redis 的 INCRBY / HINCRBY 在 key 不存在时会"从 0 新建一个永久 key"。
     * 回补路径虽然已经用 hasKey 前置判断避免了凭空造 key，但存在一个极窄的竞态窗口
     * （判断时 key 还在、写时恰好刚过期），补一次 TTL 可以兜住这种情况；
     * 同时也兜住"历史遗留的无 TTL key"——升级后第一次发生取消/退款就会给它们补上过期时间。
     *
     * <p>取 end_time 需要一次主键查询，但它只在"订单取消/超时/退款"这类低频路径上发生
     * （一笔订单最多一次），不在抢购热路径上，代价可接受。
     */
    private void ensureKeyTtl(Long activityId) {
        try {
            SeckillActivity activity = seckillActivityMapper.selectById(activityId);
            if (activity == null) {
                return;                            // 活动记录已不存在，交给 key 自身的 TTL 收尾
            }
            Duration ttl = Constants.seckillKeyTtl(activity.getEndTime());
            // 只对"已存在"的 key 设过期：避免把一个刚过期的 key 又创建出来（那等于复活已结束的活动）
            applyTtlIfPresent(Constants.seckillStockKey(activityId), ttl);
            applyTtlIfPresent(Constants.seckillBoughtKey(activityId), ttl);
        } catch (Exception e) {
            log.warn("秒杀 key 补 TTL 失败（不影响回补结果，key 会在自身 TTL 到期后回收）：activityId={}", activityId, e);
        }
    }

    /** 仅当 key 存在时设置过期时间（不存在则什么都不做，不创建 key） */
    private void applyTtlIfPresent(String key, Duration ttl) {
        if (Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
            redisTemplate.expire(key, ttl);
        }
    }

    /**
     * 回补 Redis 库存计数 seckill:{act:id}:stock。
     * <p>
     * ⚠️ 必须先 hasKey 再 increment：INCRBY 对不存在的 key 是"从 0 开始新建"，
     * 而终止活动（stopSeckill）会主动 delete 这个 key。若不判断存在性，
     * 对一笔已终止活动的历史订单做取消，就会凭空造出一个带库存的 key —— 已结束的活动被"复活"，
     * 后续如果活动状态被人工改回去就能超卖。
     */
    private void restoreRedisStock(Long activityId, int qty, String orderNo) {
        try {
            String stockKey = Constants.seckillStockKey(activityId);
            if (!Boolean.TRUE.equals(redisTemplate.hasKey(stockKey))) {
                // key 不在 = 活动已终止/已自然结束清理，属于预期情况（DB 已回补，不影响对账）
                log.warn("秒杀库存 key 不存在，跳过 Redis 回补（活动可能已终止）：activityId={}, orderNo={}, qty={}",
                        activityId, orderNo, qty);
                return;
            }
            redisTemplate.opsForValue().increment(stockKey, qty);
        } catch (Exception e) {
            log.warn("秒杀库存 Redis 回补失败：activityId={}, orderNo={}, qty={}", activityId, orderNo, qty, e);
        }
    }

    /**
     * 回退 Redis 已购数量 seckill:{act:id}:bought 里当前用户的计数，
     * 与消费端 compensate() 同一语义：订单作废后释放限购额度，用户可在活动期内重新抢。
     * <p>
     * ⚠️ 不能用 HINCRBY -qty 直接减：field 不存在时 HINCRBY 会把它当 0 再减，写出负数，
     * 之后 Lua 的 `bought + buy > perLimit` 判断会因负基数而放行超额购买。
     * 因此先读现值：<=0 不动；减到 0 直接删 field（省内存且语义干净）；否则才做减法。
     */
    private void restoreRedisBought(Long activityId, Long userId, int qty, String orderNo) {
        if (userId == null) {
            return;
        }
        try {
            String boughtKey = Constants.seckillBoughtKey(activityId);
            String field = userId.toString();
            int current = parseIntQuietly(redisTemplate.opsForHash().get(boughtKey, field));
            if (current <= 0) {
                // 没有记录（key 已清理/从未抢过）或已是 0，无需回退
                return;
            }
            if (current <= qty) {
                redisTemplate.opsForHash().delete(boughtKey, field);
            } else {
                redisTemplate.opsForHash().increment(boughtKey, field, -qty);
            }
        } catch (Exception e) {
            log.warn("秒杀已购数量 Redis 回退失败：activityId={}, orderNo={}, qty={}", activityId, orderNo, qty, e);
        }
    }

    /**
     * Redis 数值宽松解析：不同序列化器（Jackson / String / JDK）读回来可能是 Number 或 String，
     * 沿用 SeckillActivityServiceImpl.overlayLiveStock 的双分支写法，避免 ClassCastException。
     */
    private int parseIntQuietly(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        if (value instanceof String s && !s.isBlank()) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }
}
