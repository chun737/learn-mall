package com.mall.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.mall.es.ProductDoc;
import com.mall.mapper.ProductMapper;
import com.mall.service.IProductSearchService;
import com.mall.vo.PageResult;
import com.mall.vo.ProductListVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 商品搜索服务实现：
 * - 写 ES：ElasticsearchOperations.save / delete（简单可靠）
 * - 查 ES：官方 ElasticsearchClient（co.elastic.clients）做 bool + 分词匹配 + 过滤 + 排序
 * 搜索失败一律降级返回空，由上层 ProductServiceImpl 兜底走 MySQL。
 */
@Service
public class ProductSearchServiceImpl implements IProductSearchService {

    private static final Logger log = LoggerFactory.getLogger(ProductSearchServiceImpl.class);

    /** 全量同步每批条数 */
    private static final int BATCH = 500;

    private final ElasticsearchOperations ops;
    private final ElasticsearchClient esClient;
    private final ProductMapper productMapper;

    public ProductSearchServiceImpl(ElasticsearchOperations ops,
                                    ElasticsearchClient esClient,
                                    ProductMapper productMapper) {
        this.ops = ops;
        this.esClient = esClient;
        this.productMapper = productMapper;
    }

    // ---------------- 同步 ----------------

    @Override
    public int importAll() {
        int total = 0;
        int offset = 0;
        while (true) {
            List<ProductListVO> batch = productMapper.selectAllForSearch(offset, BATCH);
            if (batch == null || batch.isEmpty()) {
                break;
            }
            List<ProductDoc> docs = new ArrayList<>(batch.size());
            for (ProductListVO vo : batch) {
                docs.add(toDoc(vo));
            }
            ops.save(docs);
            total += batch.size();
            if (batch.size() < BATCH) {
                break;
            }
            offset += BATCH;
        }
        log.info("ES 全量同步完成：共 {} 条", total);
        return total;
    }

    @Override
    public void syncProductById(Long productId) {
        List<ProductListVO> one = productMapper.selectAllForSearchById(productId);
        if (one == null || one.isEmpty()) {
            // MySQL 已查不到（下架/删除）→ 同步删除 ES 里的旧文档
            deleteByProductId(productId);
            return;
        }
        ops.save(toDoc(one.get(0)));
    }

    @Override
    public void deleteByProductId(Long productId) {
        ops.delete(productId.toString(), ProductDoc.class);
    }

    // ---------------- 搜索 ----------------

    @Override
    public PageResult<ProductListVO> searchProducts(Long categoryId, String keyword,
                                                    String sortBy, int pageNum, int pageSize) {
        int page = pageNum < 1 ? 1 : pageNum;
        int size = pageSize < 1 ? 10 : pageSize;
        int from = (page - 1) * size;

        // 1. 组装 bool 查询：分词匹配（productName 加权）+ 分类过滤
        BoolQuery.Builder bool = new BoolQuery.Builder();
        if (StringUtils.hasText(keyword)) {
            bool.must(m -> m.multiMatch(mm -> mm
                    .query(keyword)
                    .fields("productName^2", "subTitle")));
        }
        if (categoryId != null) {
            bool.filter(f -> f.term(t -> t.field("categoryId").value(categoryId)));
        }
        Query query = new Query.Builder().bool(bool).build();

        // 2. 执行搜索
        try {
            SearchResponse<ProductDoc> resp = esClient.search(s -> {
                s.index("product").query(query).from(from).size(size);
                String field = sortField(sortBy);
                if (field != null) {
                    s.sort(ss -> ss.field(f -> f.field(field).order(sortOrder(sortBy))));
                }
                return s;
            }, ProductDoc.class);

            // 3. 结果转 VO
            List<ProductListVO> list = new ArrayList<>();
            if (resp.hits() != null && resp.hits().hits() != null) {
                for (Hit<ProductDoc> hit : resp.hits().hits()) {
                    if (hit.source() != null) {
                        list.add(toVO(hit.source()));
                    }
                }
            }
            long total = resp.hits().total() == null ? 0 : resp.hits().total().value();
            return PageResult.of(list, total, page, size);
        } catch (IOException | RuntimeException e) {
            log.warn("ES 搜索异常，降级返回空: {}", e.getMessage());
            return PageResult.of(new ArrayList<>(), 0L, page, size);
        }
    }

    /** 排序字段映射：null = 不额外排序（默认相关性） */
    private String sortField(String sortBy) {
        if (sortBy == null) {
            return null;
        }
        return switch (sortBy) {
            case "priceAsc", "priceDesc" -> "minPrice";
            case "salesDesc" -> "sales";
            case "newest" -> "id";
            default -> null;
        };
    }

    private SortOrder sortOrder(String sortBy) {
        return "priceAsc".equals(sortBy) ? SortOrder.Asc : SortOrder.Desc;
    }

    // ---------------- 转换 ----------------

    private ProductDoc toDoc(ProductListVO vo) {
        ProductDoc d = new ProductDoc();
        d.setId(vo.getId());
        d.setCategoryId(vo.getCategoryId());
        d.setProductName(vo.getProductName());
        d.setSubTitle(vo.getSubTitle());
        d.setMainImage(vo.getMainImage());
        d.setMinPrice(vo.getMinPrice());
        d.setMaxPrice(vo.getMaxPrice());
        d.setSales(vo.getSales());
        return d;
    }

    private ProductListVO toVO(ProductDoc d) {
        ProductListVO vo = new ProductListVO();
        vo.setId(d.getId());
        vo.setCategoryId(d.getCategoryId());
        vo.setProductName(d.getProductName());
        vo.setSubTitle(d.getSubTitle());
        vo.setMainImage(d.getMainImage());
        vo.setMinPrice(d.getMinPrice());
        vo.setMaxPrice(d.getMaxPrice());
        vo.setSales(d.getSales());
        return vo;
    }
}
