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

import java.time.LocalDateTime;
import java.util.List;

/**
 * 秒杀库存启动预热：
 * 应用启动时扫描所有未删除、未结束的活动，把 MySQL 的 available_stock 预热到
 * Redis 的 seckill:stock:{id}。用 SETNX 只补缺失的 key，不覆盖已消耗的库存。
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
        // 只预热未结束的活动（end_time > now 的还能抢）
        List<SeckillActivity> activities = seckillActivityMapper.selectList(
                new LambdaQueryWrapper<SeckillActivity>()
                        .eq(SeckillActivity::getDeleted, Constants.NOT_DELETED)
                        .gt(SeckillActivity::getEndTime, LocalDateTime.now()));

        int preloaded = 0;
        int skipped = 0;
        for (SeckillActivity activity : activities) {
            String key = Constants.SECKILL_STOCK_PREFIX + activity.getId();
            // setIfAbsent = SETNX：key 不存在才写入，已存在不覆盖（保留已消耗库存）
            Boolean absent = redisTemplate.opsForValue().setIfAbsent(key, activity.getAvailableStock());
            if (Boolean.TRUE.equals(absent)) {
                preloaded++;
            } else {
                skipped++;
            }
        }
        log.info("秒杀库存预热完成：新增 {} 个，跳过（已存在）{} 个", preloaded, skipped);
    }
}
