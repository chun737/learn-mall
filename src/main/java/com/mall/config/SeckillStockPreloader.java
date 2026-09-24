package com.mall.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mall.common.Constants;
import com.mall.entity.SeckillActivity;
import com.mall.mapper.SeckillActivityMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 秒杀库存启动预热 + 遗留 key 的 TTL 收尾。
 *
 * <p>做两件事，边界不同：
 * <ol>
 *   <li><b>预热 stock</b>：只对<b>未结束</b>的活动（end_time &gt; now）把 MySQL 的
 *       available_stock 用 SETNX 写进 Redis 的秒杀库存 key（只补缺失的 key，
 *       不覆盖已消耗的库存）；</li>
 *   <li><b>补 TTL</b>：对<b>所有</b>活动的 bought/result key 执行 expire，涵盖已结束活动
 *       遗留的、早先没有 TTL 的永久 key。expire 对不存在的 key 无副作用，因此这一步
 *       <b>绝不创建 key</b>。</li>
 * </ol>
 *
 * <p><b>为什么 stock 的创建必须限制在"未结束"</b>：否则会为已结束活动重建库存 key，
 * 等于复活已结束活动的库存 —— 与 {@link Constants#seckillKeyTtl} 的设计原则相悖。
 */
@Component
public class SeckillStockPreloader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeckillStockPreloader.class);

    private final SeckillActivityMapper seckillActivityMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    public SeckillStockPreloader(SeckillActivityMapper seckillActivityMapper,
                                 RedisTemplate<String, Object> redisTemplate) {
        this.seckillActivityMapper = seckillActivityMapper;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        LocalDateTime now = LocalDateTime.now();
        // 查询范围放宽到全部逻辑未删除活动：既要预热"未结束"的活动，也要能给"已结束"活动
        // 遗留的无 TTL key 补过期。原先只查 end_time > now，那批历史 key 永远轮不到处理。
        // 只 select 用到的三列，避免把整个实体拉出来。
        List<SeckillActivity> activities = seckillActivityMapper.selectList(
                new LambdaQueryWrapper<SeckillActivity>()
                        .select(SeckillActivity::getId, SeckillActivity::getEndTime,
                                SeckillActivity::getAvailableStock));

        int preloaded = 0;
        int skipped = 0;
        int ttlFixed = 0;
        for (SeckillActivity activity : activities) {
            // TTL = 到活动 end_time + 缓冲 的剩余时长：活动自然结束后 key 自动回收，
            // 否则会永久滞留（原先无 TTL，Redis 内存只增不减）。
            // 已结束活动会落到 SECKILL_KEY_TTL_FALLBACK(24h)，所以遗留的永久 key 也会被限时回收。
            Duration ttl = Constants.seckillKeyTtl(activity.getEndTime());

            // ① 补 TTL：expire 对不存在的 key 无副作用，可无条件执行 —— 它绝不创建 key。
            //    只碰 bought/result；stock 的创建必须受"活动未结束"约束（见 ②），
            //    否则等于给已结束活动复活库存。
            if (Boolean.TRUE.equals(redisTemplate.expire(Constants.seckillBoughtKey(activity.getId()), ttl))) {
                ttlFixed++;
            }
            if (Boolean.TRUE.equals(redisTemplate.expire(Constants.seckillResultKey(activity.getId()), ttl))) {
                ttlFixed++;
            }

            // ② 预热 stock：只对未结束活动，行为与原先一致。
            //    setIfAbsent = SETNX：key 不存在才写入，已存在不覆盖（保留已消耗库存）
            boolean upcoming = activity.getEndTime() != null && activity.getEndTime().isAfter(now);
            if (upcoming) {
                Boolean absent = redisTemplate.opsForValue().setIfAbsent(
                        Constants.seckillStockKey(activity.getId()), activity.getAvailableStock(), ttl);
                if (Boolean.TRUE.equals(absent)) {
                    preloaded++;
                } else {
                    skipped++;
                }
            }
        }
        log.info("秒杀库存预热完成：新增 {} 个，跳过（已存在）{} 个，补 TTL {} 个",
                preloaded, skipped, ttlFixed);
    }
}
