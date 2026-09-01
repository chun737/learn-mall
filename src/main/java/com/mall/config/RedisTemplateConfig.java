package com.mall.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

@Configuration
public class RedisTemplateConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        // Jackson 2 序列化器（与 CacheConfig 同配置：activateDefaultTyping + 白名单）
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
        GenericJackson2JsonRedisSerializer serializer = GenericJackson2JsonRedisSerializer.builder()
                .objectMapper(mapper)
                .build();
        // key 用 String，value 用 JSON；hash 结构同理各配一份
        template.setKeySerializer(RedisSerializer.string());
        template.setValueSerializer(serializer);
        template.setHashKeySerializer(RedisSerializer.string());
        template.setHashValueSerializer(serializer);
        template.afterPropertiesSet();
        return template;
    }
}