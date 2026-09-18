package com.mall.config;

import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ES 客户端解码配置：
 * 默认 JacksonJsonpMapper 自建的 ObjectMapper 遇到"文档里有、Java 类里没有"的字段
 * （如 Spring Data ES 写入的 _class 类型标记）会抛异常，导致 search 响应解码失败、
 * 搜索永远返回空。这里复用 Spring 全局的 ObjectMapper（默认忽略未知属性），
 * 让响应解码跳过 _class。
 * Spring Boot 自动装配的 JsonpMapper 带 @ConditionalOnMissingBean，
 * 本 Bean 一旦存在即接管 ES 客户端的 JSON 解码。
 */
@Configuration
public class EsMapperConfig {

    @Bean
    public JacksonJsonpMapper elasticsearchJsonpMapper(ObjectMapper objectMapper) {
        return new JacksonJsonpMapper(objectMapper);
    }
}
