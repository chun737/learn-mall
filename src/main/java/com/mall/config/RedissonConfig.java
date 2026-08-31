package com.mall.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson 客户端配置：连接地址复用 spring.data.redis 配置，
 * 供布隆过滤器等 Redis 分布式数据结构使用（与 Spring Cache 的 Lettuce 互不干扰）。
 * 注意：Redisson 在客户端创建时会立即建立连接，Redis 未启动将导致应用启动失败。
 */
@Configuration
public class RedissonConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://" + redisHost + ":" + redisPort);
        return Redisson.create(config);
    }
}
