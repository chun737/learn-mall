package com.mall.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.mall.common.EsUnavailableException;
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
 *
 * 可用性策略（ES 只是"搜索副本"，绝不能拖垮主流程）：
 * 1. 查询路径 searchProducts：ES 失败抛 EsUnavailableException，由上层 ProductServiceImpl 降级走 MySQL；
 * 2. 写入路径 syncProductById / deleteByProductId：失败静默跳过（只记一行日志），主流程不受影响；
 * 3. 熔断：任意一次 ES 调用失败后，30 秒内不再尝试 ES 而直接快速失败。
 *
 * 第 3 点是必需的，不是锦上添花：ES 关机时 connect 会一路等到 connection-timeout(5s)。
 * 若不熔断，每个带关键词的请求、每次商品增删改都要白等 5 秒——比"干脆不用 ES"更糟。
 */
@Service
public class ProductSearchServiceImpl implements IProductSearchService {

    private static final Logger log = LoggerFactory.getLogger(ProductSearchServiceImpl.class);

    /** 全量同步每批条数 */
    private static final int BATCH = 500;

    /** 熔断窗口时长：ES 失败后这段时间内跳过一切 ES 调用，避免反复白等连接超时 */
    private static final long CIRCUIT_OPEN_MS = 30_000L;

    /** 熔断截止时刻（epoch ms）；小于当前时间 = 电路闭合，允许尝试 ES */
    private volatile long circuitOpenUntil = 0L;

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
        ensureEsUsable("全量同步");
        try {
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
        } catch (Exception e) {
            // 全量同步是管理员主动点的按钮，失败必须显式告诉他，不能静默返回"同步了 0 条"
            throw markUnavailable("全量同步", e);
        }
    }

    @Override
    public void syncProductById(Long productId) {
        // 熔断窗口内静默跳过：同步是"尽力而为"的旁路，不该让每次商品增删改都白等连接超时
        if (circuitOpen()) {
            return;
        }
        try {
            List<ProductListVO> one = productMapper.selectAllForSearchById(productId);
            if (one == null || one.isEmpty()) {
                // MySQL 已查不到（下架/删除）→ 同步删除 ES 里的旧文档
                deleteByProductId(productId);
                return;
            }
            ops.save(toDoc(one.get(0)));
        } catch (Exception e) {
            // 只记日志不抛出：ES 是副本，同步失败不能连累商品的增删改主流程，
            // 数据不一致靠"改完 MySQL 后手动全量重灌"兜底。
            openCircuit();
            log.warn("ES 单条同步失败（ES 只是搜索副本，不影响主流程）productId={}: {}", productId, e.getMessage());
        }
    }

    @Override
    public void deleteByProductId(Long productId) {
        if (circuitOpen()) {
            return;
        }
        try {
            ops.delete(productId.toString(), ProductDoc.class);
        } catch (Exception e) {
            openCircuit();
            log.warn("ES 文档删除失败（不影响主流程）productId={}: {}", productId, e.getMessage());
        }
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
        Query query = new Query.Builder().bool(bool.build()).build();

        // 2. 执行搜索：熔断窗口内直接失败，把"降级走 MySQL"的决定权交给上层
        ensureEsUsable("搜索");
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
            // 注意：这里必须"抛出去"，不能返回空列表。
            // 返回空 = 用户看到"没有找到相关商品"，上层 ProductServiceImpl 的 MySQL 降级分支永远不会被触发；
            // 抛异常才能让上层接住并改走 MySQL，把数据真正搜出来。
            throw markUnavailable("搜索", e);
        }
    }

    // ---------------- 可用性（熔断） ----------------

    /** 电路是否打开：ES 刚失败过，窗口内跳过 ES 调用，直接快速失败 */
    private boolean circuitOpen() {
        return System.currentTimeMillis() < circuitOpenUntil;
    }

    /** 打开熔断窗口：接下来 CIRCUIT_OPEN_MS 内的 ES 调用一律跳过 */
    private void openCircuit() {
        circuitOpenUntil = System.currentTimeMillis() + CIRCUIT_OPEN_MS;
    }

    /** 查询路径专用：熔断窗口内直接抛异常（不消耗任何网络等待时间） */
    private void ensureEsUsable(String op) {
        if (circuitOpen()) {
            throw new EsUnavailableException("ES 处于熔断窗口（" + op + "），本轮直接降级");
        }
    }

    /** 查询路径专用：打开熔断并抛出可被上层识别的异常，触发 MySQL 降级 */
    private EsUnavailableException markUnavailable(String op, Exception cause) {
        openCircuit();
        log.warn("ES {} 失败，熔断 {}s 内直接降级: {}", op, CIRCUIT_OPEN_MS / 1000, cause.getMessage());
        return new EsUnavailableException("ES 不可用（" + op + "）: " + cause.getMessage(), cause);
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
