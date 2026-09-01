package com.mall.config;

import com.mall.common.Constants;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis 缓存配置：以 Redis 作为 Spring Cache 的实现
 *
 * 缓存命名与 TTL 约定（key 形如 mall:cache名::业务键）：
 * - category    分类树，读极多写极少，TTL 1 小时 + 写后逐出
 * - product     商品详情（SPU+SKU 联查结果），TTL 30 分钟 + 写后逐出
 * - productList 商品列表页，允许短时滞后，TTL 5 分钟
 * - hot         热搜词，TTL 15 分钟
 *
 * 值统一用 Jackson JSON 序列化（带 @class 类型信息，可读性好、跨服务兼容），
 * 键用默认的 String 序列化。
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /** key 统一前缀已迁至 Constants.CACHE_PREFIX（全项目单一数据源） */

    /**
     * Jackson 2（com.fasterxml.jackson）通用 JSON 序列化器：值以 JSON 存储，内嵌 @class 类型信息。
     * Spring Data Redis 3.x 的 builder 无 enableDefaultTyping(validator)，改用自定义 ObjectMapper：
     * 通过 activateDefaultTyping + BasicPolymorphicTypeValidator 白名单开启类型信息（防多态反序列化攻击）。
     */
    private static final GenericJackson2JsonRedisSerializer JSON_SERIALIZER = buildJsonSerializer();

    private static GenericJackson2JsonRedisSerializer buildJsonSerializer() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.activateDefaultTyping(
                BasicPolymorphicTypeValidator.builder()
                        .allowIfSubType("com.mall.")
                        .allowIfSubType("java.util.")
                        .allowIfSubType("java.lang.")
                        .allowIfSubType("java.math.")
                        .build(),
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY);
        return GenericJackson2JsonRedisSerializer.builder()
                .objectMapper(mapper)
                .build();
    }

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // 不同缓存名配置不同 TTL（常量统一在 Constants）；未列出的缓存名走 cacheDefaults
        Map<String, RedisCacheConfiguration> cacheConfigs = new HashMap<>();
        cacheConfigs.put(Constants.CACHE_NAME_CATEGORY, buildCacheConfig(Constants.CACHE_TTL_CATEGORY));
        cacheConfigs.put(Constants.CACHE_NAME_PRODUCT, buildCacheConfig(Constants.CACHE_TTL_PRODUCT));
        cacheConfigs.put(Constants.CACHE_NAME_PRODUCT_LIST, buildCacheConfig(Constants.CACHE_TTL_PRODUCT_LIST));
        cacheConfigs.put(Constants.CACHE_NAME_HOT, buildCacheConfig(Constants.CACHE_TTL_HOT));

        return RedisCacheManager.builder(
                        RedisCacheWriter.nonLockingRedisCacheWriter(connectionFactory))
                .cacheDefaults(buildCacheConfig(Constants.CACHE_TTL_DEFAULT))
                .withInitialCacheConfigurations(cacheConfigs)
                .build();
    }

    private RedisCacheConfiguration buildCacheConfig(Duration ttl) {
        return RedisCacheConfiguration.defaultCacheConfig()
                .prefixCacheNameWith(Constants.CACHE_PREFIX)
                .entryTtl(ttl)
                // 不缓存 null 值，避免穿透防护之外的多余占用（本项目未命中直接回源 DB）
                .disableCachingNullValues()
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(JSON_SERIALIZER));
    }
}
