package com.mall.config;

import com.mall.es.ProductDoc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.stereotype.Component;

/**
 * 启动时确保 ES 的 product 索引存在（幂等）：
 * 按 ProductDoc 的注解生成 mapping（字段类型 + IK 分词）。
 * ES 未启动时仅告警不阻断应用启动（搜索会自动降级走 MySQL）。
 */
@Component
public class ProductIndexInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ProductIndexInitializer.class);

    private final ElasticsearchOperations ops;

    public ProductIndexInitializer(ElasticsearchOperations ops) {
        this.ops = ops;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            boolean created = ops.indexOps(ProductDoc.class).createWithMapping();
            log.info(created ? "ES product 索引创建成功" : "ES product 索引已存在，跳过创建");
        } catch (Exception e) {
            log.warn("ES 索引初始化失败（ES 未启动或地址不通？）: {}", e.getMessage());
        }
    }
}
