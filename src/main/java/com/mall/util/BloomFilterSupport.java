package com.mall.util;

import java.util.List;

/**
 * 布隆过滤器策略接口：每个需要布隆防护的业务提供一个实现 bean，
 * BloomFilterRegistry 会自动收集所有实现并统一完成 创建/初始化/预热。
 *
 * 实现方只需回答三件事：过滤器叫什么、预期多大、全量 ID 从哪张表来——
 * 机制（幂等初始化、空表预热、查询/添加转发）全部内聚在注册中心，业务零重复。
 *
 * @author 乐乐
 */
public interface BloomFilterSupport {

    /** 过滤器在 Redis 中的名称（使用 Constants 中的 BLOOM_FILTER_* 常量） */
    String filterName();

    /** 预期元素量（宁大勿小：超出会推高误判率，只是多占内存） */
    long expectedInsertions();

    /**
     * 预热数据源：当前全量有效业务 ID（启动时灌入过滤器）。
     * 实现必须走 XML SQL（项目规范：CRUD 只用 SQL 语句），且只过滤逻辑删除。
     */
    List<Long> loadAllIds();
}
