package com.mall.service;

import com.mall.vo.PageResult;
import com.mall.vo.ProductListVO;

/**
 * 商品搜索服务（Elasticsearch）接口：索引写入与查询的唯一出口。
 *
 * 为什么独立成一个服务，而不是塞进 IProductService：
 *   ES 是"可选依赖"——索引未建、ES 未启动时搜索会降级返回空，
 *   由上层兜底走 MySQL（见 ProductSearchServiceImpl 的异常处理）。
 *   把"搜索"和"商品 CRUD"分开，ES 故障的影响面就限制在这一个接口里。
 *
 * @author 乐乐
 */
public interface IProductSearchService {

    /**
     * 全量同步：把 MySQL 中全部在售商品灌入 ES 的 product 索引。
     * 幂等，重复调用会覆盖已有文档。
     *
     * @return 实际写入的条数
     */
    int importAll();

    /**
     * 单条同步：按商品 ID 从 MySQL 取最新数据写入 ES。
     * MySQL 里已查不到（下架/删除）时，改为删除 ES 中的旧文档。
     */
    void syncProductById(Long productId);

    /** 按商品 ID 删除 ES 文档 */
    void deleteByProductId(Long productId);

    /**
     * 商品搜索：bool 查询（商品名加权 + 副标题）+ 分类过滤 + 排序 + 分页。
     *
     * @param categoryId 分类 ID（可空 = 不过滤）
     * @param keyword    关键词（可空 = 匹配全部）
     * @param sortBy     排序方式：default/priceAsc/priceDesc/salesDesc/newest
     * @param pageNum    页码，从 1 开始
     * @param pageSize   每页条数
     * @return 分页结果；ES 异常时降级返回空列表（不抛异常，由上层走 MySQL 兜底）
     */
    PageResult<ProductListVO> searchProducts(Long categoryId, String keyword,
                                             String sortBy, int pageNum, int pageSize);
}
