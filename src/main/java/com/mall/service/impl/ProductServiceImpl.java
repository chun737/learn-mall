package com.mall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.github.pagehelper.PageInfo;
import com.mall.common.BusinessException;
import com.mall.common.PageUtils;
import com.mall.common.Constants;
import com.mall.util.BloomFilterRegistry;
import com.mall.dto.SkuDTO;
import com.mall.enums.ErrorCode;
import com.mall.dto.ProductDTO;
import com.mall.entity.Product;
import com.mall.entity.ProductSku;
import com.mall.mapper.ProductMapper;
import com.mall.mapper.ProductSkuMapper;
import com.mall.service.IProductService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mall.vo.*;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.BeanUtils;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.redis.core.RedisTemplate;
import com.mall.service.IProductSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static com.mall.enums.ErrorCode.NOT_FOUND;

/**
 * <p>
 * 商品表（SPU） 服务实现类
 * </p>
 *
 * @author 乐乐
 * @since 2026-08-16
 */
@Service
public class ProductServiceImpl extends ServiceImpl<ProductMapper, Product> implements IProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductServiceImpl.class);

    private final ProductMapper productMapper;
    private final ProductSkuMapper productSkuMapper;
    private final BloomFilterRegistry bloomFilterRegistry;
    private final RedisTemplate<String, Object> redisTemplate;
    /** ES 搜索服务：同步与 keyword 搜索 */
    private final IProductSearchService productSearchService;

    // 缓存 key 前缀/占位符/TTL 等固定值已统一迁至 Constants（CACHE_KEY_PRODUCT / LOCK_KEY_PRODUCT 等）

    public ProductServiceImpl(ProductMapper productMapper, ProductSkuMapper productSkuMapper,
                              BloomFilterRegistry bloomFilterRegistry,
                              RedisTemplate<String, Object> redisTemplate,
                              IProductSearchService productSearchService) {
        this.productMapper = productMapper;
        this.productSkuMapper = productSkuMapper;
        this.bloomFilterRegistry = bloomFilterRegistry;
        this.redisTemplate = redisTemplate;
        this.productSearchService = productSearchService;
    }

    /** ES 同步失败不阻断主流程（ES 只是搜索副本），记日志即可 */
    private void syncEsQuietly(Long productId) {
        try {
            productSearchService.syncProductById(productId);
        } catch (Exception e) {
            log.warn("ES 同步失败 productId={}: {}", productId, e.getMessage());
        }
    }

    /** ES 删除失败不阻断主流程 */
    private void deleteEsQuietly(Long productId) {
        try {
            productSearchService.deleteByProductId(productId);
        } catch (Exception e) {
            log.warn("ES 删除失败 productId={}: {}", productId, e.getMessage());
        }
    }

    @Override
    @Cacheable(cacheNames = Constants.CACHE_NAME_PRODUCT_LIST,
            key = "#pageNum + ':' + #pageSize + ':' + #categoryId + ':' + (#keyword ?: '') + ':' + #sortBy",
            // 只缓存无关键词的前 3 页（覆盖绝大多数流量），避免筛选组合导致缓存 key 无限膨胀
            condition = "(#keyword == null || #keyword.isEmpty()) && #pageNum <= 3")
    public PageResult<ProductListVO> listProducts(
            Integer pageNum,
            Integer pageSize,
            Long categoryId,
            String keyword,
            String sortBy) {
        // 0. 有关键词 → 走 ES 分词搜索（分类浏览/无关键词仍走 MySQL + 缓存）
        //    ES 不可用时降级回 MySQL（try 内抛异常则继续往下执行原逻辑）
        if (keyword != null && !keyword.isEmpty()) {
            try {
                return productSearchService.searchProducts(categoryId, keyword, sortBy, pageNum, pageSize);
            } catch (Exception e) {
                log.warn("ES 搜索降级走 MySQL，keyword={}: {}", keyword, e.getMessage());
            }
        }
        // 1. 开启分页：紧随其后的第一条 SQL 会被 PageHelper 自动改写（追加 LIMIT + 发 COUNT 查询）
        PageUtils.startPage(pageNum, pageSize);

        // 2. 执行 XML 中的查询（返回的 List 实际是 Page<T>，携带分页信息；直接就是 VO 列表）
        List<ProductListVO> voList = baseMapper.selectProductList(categoryId, keyword, sortBy);

        // 3. 包装成 PageInfo，提取 total / pages 等分页信息
        PageInfo<ProductListVO> pageInfo = new PageInfo<>(voList);

        // 4. 组装为统一分页结果返回
        return PageResult.of(pageInfo, voList);
    }

    /**
     * 商品详情：编程式缓存控制（原 @Cacheable 改手写，以支持空值缓存/互斥锁/TTL 抖动三种注解表达不了的控制）。
     * 完整防护链：读缓存 → 空值占位拦截 → 布隆过滤器（防穿透）→ 互斥锁回源（防击穿）→ 空值/抖动 TTL 回填（防雪崩）。
     * 写操作上的 @CacheEvict(cacheNames = Constants.CACHE_NAME_PRODUCT) 逐出 mall:product::{id}，与本方法的键格式一致，无需改动。
     */
    @Override
    public ProductDetailVO getDPs(Long id) {
        String cacheKey = Constants.CACHE_KEY_PRODUCT + id;

        // 1. 读缓存；Redis 故障时静默降级为未命中，直查 DB（缓存层不可用不阻断业务）
        Object cached = readCacheQuietly(cacheKey);
        ProductDetailVO hit = asDetail(cached);
        if (hit != null) {
            return hit;
        }
        if (Constants.CACHE_NULL_PLACEHOLDER.equals(cached)) {
            throw new BusinessException(NOT_FOUND);   // 空值占位：短时间内不再回源
        }

        // 2. 布隆过滤器：一定不存在的 ID 直接拒绝，零 DB 成本（防穿透第一道闸）
        if (!bloomFilterRegistry.mightContain(Constants.BLOOM_FILTER_PRODUCT, id)) {
            throw new BusinessException(NOT_FOUND);
        }

        // 3. 互斥锁回源（防击穿）：同一商品并发未命中时只放一个请求查库，其余等待重读
        String lockKey = Constants.LOCK_KEY_PRODUCT + id;
        for (int i = 0; i < 3; i++) {
            Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", Constants.LOCK_TTL_PRODUCT);
            if (Boolean.TRUE.equals(locked)) {
                try {
                    // 拿到锁后复查缓存：排队期间大概率已被前一个请求重建
                    hit = asDetail(readCacheQuietly(cacheKey));
                    if (hit != null) {
                        return hit;
                    }
                    if (Constants.CACHE_NULL_PLACEHOLDER.equals(readCacheQuietly(cacheKey))) {
                        throw new BusinessException(NOT_FOUND);
                    }

                    // 4. 回源查库（XML SQL：ProductMapper.getDPs）
                    ProductDetailVO vo = productMapper.getDPs(id);
                    if (vo == null) {
                        // 空值缓存：布隆误判或商品已删除，写 60 秒占位，同一 ID 后续请求直接 404
                        writeCacheQuietly(cacheKey, Constants.CACHE_NULL_PLACEHOLDER, Constants.CACHE_NULL_PLACEHOLDER_TTL);
                        throw new BusinessException(NOT_FOUND);
                    }
                    // 命中值回填：TTL 30 分钟 ± 5 分钟随机抖动，错开同类 key 的到期时刻（防雪崩）
                    writeCacheQuietly(cacheKey, vo, jitteredTtl());
                    return vo;
                } finally {
                    // 只释放自己持有的锁；即便中途抛异常也保证释放，锁 TTL 兜底极端泄漏
                    redisTemplate.delete(lockKey);
                }
            }
            // 未抢到锁：等待后重读，最多 3 次（约 300ms）
            try {
                Thread.sleep(100);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
            hit = asDetail(readCacheQuietly(cacheKey));
            if (hit != null) {
                return hit;
            }
            if (Constants.CACHE_NULL_PLACEHOLDER.equals(readCacheQuietly(cacheKey))) {
                throw new BusinessException(NOT_FOUND);
            }
        }

        // 5. 兜底降级：等待重试后仍未拿到重建结果，直接查库（宁可短暂多一次回源，不给用户报错）
        ProductDetailVO vo = productMapper.getDPs(id);
        if (vo == null) {
            throw new BusinessException(NOT_FOUND);
        }
        return vo;
    }

    /** 缓存值类型还原：不是 ProductDetailVO（如占位符/脏数据）返回 null，交由调用方走占位逻辑 */
    private ProductDetailVO asDetail(Object cached) {
        return cached instanceof ProductDetailVO vo ? vo : null;
    }

    /** 读缓存：Redis 故障视为未命中 */
    private Object readCacheQuietly(String key) {
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            return null;
        }
    }

    /** 写缓存：Redis 故障不影响本次响应，仅损失一次缓存写入 */
    private void writeCacheQuietly(String key, Object value, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key, value, ttl);
        } catch (Exception ignored) {
        }
    }

    /** 基础 TTL（Constants.CACHE_TTL_PRODUCT）± CACHE_TTL_JITTER 随机抖动，错开同类 key 到期时刻（防雪崩） */
    private Duration jitteredTtl() {
        long jitter = Constants.CACHE_TTL_JITTER.toSeconds();
        return Constants.CACHE_TTL_PRODUCT.plusSeconds(
                ThreadLocalRandom.current().nextLong(-jitter, jitter));
    }

    @Override
    @Cacheable(cacheNames = Constants.CACHE_NAME_HOT, key = "'keyword'",
               condition = "#keyword == null || #keyword.isEmpty()")
    public List<String> getHot10(String keyword) {
        List<String> hot10 = productMapper.getHot10(keyword);
        return hot10 != null ? hot10 : new ArrayList<>();
    }

    @Override
    public PageResult<ProductListAdminVO> listProdctsAdmin(Integer pageNum, Integer pageSize, Long categoryId, String keyword, Integer status, LocalDateTime startTime, LocalDateTime endTime) {
        // 1. 开启分页
        PageUtils.startPage(pageNum, pageSize);

        // 2. 执行后台商品列表查询（返回 VO 列表，PageHelper 自动改写 LIMIT + COUNT）
        List<ProductListAdminVO> voList = productMapper.ListProduct(categoryId, keyword, status, startTime, endTime);

        // 3. 包装分页信息
        PageInfo<ProductListAdminVO> pageInfo = new PageInfo<>(voList);

        // 4. 组装统一分页结果
        return PageResult.of(pageInfo, voList);
    }

    @Override
    @CacheEvict(cacheNames = Constants.CACHE_NAME_PRODUCT_LIST, allEntries = true)
    public ProductDetailVO addProduct(ProductDTO productDTO) {
        Product product = new Product();
        BeanUtils.copyProperties(productDTO,product);
        product.setCreatedAt(LocalDateTime.now());
        product.setDeleted(0);
        productMapper.insert(product);
        // 同步维护布隆过滤器：漏掉这步新商品会被过滤器误杀（前台查询直接 404）
        bloomFilterRegistry.add(Constants.BLOOM_FILTER_PRODUCT, product.getId());
        // 同步 ES（此时 SKU 可能还没有，先灌一条；加 SKU 后 addSkus 会再同步价格）
        syncEsQuietly(product.getId());
        ProductDetailVO productDetailVO = new ProductDetailVO();
        BeanUtils.copyProperties(product,productDetailVO);
        return productDetailVO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @Caching(evict = {
            @CacheEvict(cacheNames = Constants.CACHE_NAME_PRODUCT, key = "#id"),
            @CacheEvict(cacheNames = Constants.CACHE_NAME_PRODUCT_LIST, allEntries = true)
    })
    public void modifyProduct(Long id, ProductDTO productDTO) {
        // 1. 参数非空校验
        if (productDTO == null) {
            throw new BusinessException(NOT_FOUND);
        }

        // 2. 校验商品存在且未删除
        Product product = productMapper.selectById(id);
        if (product == null || Constants.DELETED == product.getDeleted()) {
            throw new BusinessException(NOT_FOUND);
        }

        // 3. 白名单更新允许修改的字段（只更新传了值的字段，避免把 null 写进去）
        if (productDTO.getCategoryId() != null) {
            product.setCategoryId(productDTO.getCategoryId());
        }
        if (productDTO.getProductName() != null) {
            product.setProductName(productDTO.getProductName());
        }
        if (productDTO.getSubTitle() != null) {
            product.setSubTitle(productDTO.getSubTitle());
        }
        if (productDTO.getMainImage() != null) {
            product.setMainImage(productDTO.getMainImage());
        }
        if (productDTO.getDetail() != null) {
            product.setDetail(productDTO.getDetail());
        }
        if (productDTO.getStatus() != null) {
            product.setStatus(productDTO.getStatus());
        }
        product.setUpdatedAt(LocalDateTime.now());

        // 4. 更新落库
        productMapper.updateById(product);

        // 5. 同步 ES：名称/分类/状态变了，搜索副本要跟上（失败不阻断）
        syncEsQuietly(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @Caching(evict = {
            @CacheEvict(cacheNames = Constants.CACHE_NAME_PRODUCT, key = "#id"),
            @CacheEvict(cacheNames = Constants.CACHE_NAME_PRODUCT_LIST, allEntries = true)
    })
    public String modifyStatus(Long id, Integer status) {
        // 1. 状态合法性校验（0=下架 1=上架，不支持草稿等其他值通过此接口设置）
        if (status == null || (status != Constants.PRODUCT_STATUS_OFF_SHELF
                && status != Constants.PRODUCT_STATUS_ON_SHELF)) {
            throw new BusinessException(ErrorCode.PRODUCT_STATUS_ILLEGAL);
        }

        // 2. 商品存在性校验
        Product product = productMapper.selectById(id);
        if (product == null || Constants.DELETED == product.getDeleted()) {
            throw new BusinessException(NOT_FOUND);
        }

        // 3. 上架前校验：必须存在已上架且未删除的 SKU，否则商品上架了却没东西可卖
        if (status == Constants.PRODUCT_STATUS_ON_SHELF) {
            Long onShelfSkuCount = productSkuMapper.selectCount(
                    new LambdaQueryWrapper<ProductSku>()
                            .eq(ProductSku::getProductId, id)
                            .eq(ProductSku::getStatus, Constants.PRODUCT_STATUS_ON_SHELF)
                            .eq(ProductSku::getDeleted, Constants.NOT_DELETED));
            if (onShelfSkuCount == null || onShelfSkuCount == 0) {
                throw new BusinessException(ErrorCode.PRODUCT_SKU_NOT_READY);
            }
        }

        // 4. 更新商品状态并落库（关键：之前漏了 update，导致状态根本没改）
        product.setStatus(status);
        product.setUpdatedAt(LocalDateTime.now());
        productMapper.updateById(product);

        // 5. 同步更新该商品下所有未删除 SKU 的状态
        ProductSku updateSku = new ProductSku();
        updateSku.setStatus(status);
        productSkuMapper.update(updateSku,
                new LambdaQueryWrapper<ProductSku>()
                        .eq(ProductSku::getProductId, id)
                        .eq(ProductSku::getDeleted, Constants.NOT_DELETED));

        // 6. 同步 ES：上架 → 索引；下架 → 移除（保证搜不到下架商品）
        if (status == Constants.PRODUCT_STATUS_ON_SHELF) {
            syncEsQuietly(id);
        } else {
            deleteEsQuietly(id);
        }

        return status == Constants.PRODUCT_STATUS_ON_SHELF ? "商品上架成功" : "商品下架成功";
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @Caching(evict = {
            @CacheEvict(cacheNames = Constants.CACHE_NAME_PRODUCT, key = "#id"),
            @CacheEvict(cacheNames = Constants.CACHE_NAME_PRODUCT_LIST, allEntries = true)
    })
    public void deleteProduct(Long id) {
        // 1. 校验商品存在且未删除
        Product product = productMapper.selectById(id);
        if (product == null || Constants.DELETED == product.getDeleted()) {
            throw new BusinessException(NOT_FOUND);
        }
        // 2. 逻辑删除商品
        productMapper.deleteProduct(id);
        // 3. 逻辑删除该商品下的所有 SKU
        productMapper.deleteProductSkus(id);
        // 4. 从 ES 移除（保证搜索结果不再出现已删除商品）
        deleteEsQuietly(id);
    }

    @Override
    public List<AdminSkuVO> getSKUs(Long id) {
        List<ProductSku> productSkus = productSkuMapper.selectList(new QueryWrapper<ProductSku>()
                .eq("product_id", id));
        if(productSkus.isEmpty()){
            throw new BusinessException(NOT_FOUND);
        }
        return productSkus.stream().map(productSku -> {
            AdminSkuVO adminSkuVO = new AdminSkuVO();
            BeanUtils.copyProperties(productSku, adminSkuVO);
            return adminSkuVO;
        }).sorted(Comparator.comparing(AdminSkuVO::getId)).toList();
    }

    @Override
    @Caching(evict = {
            @CacheEvict(cacheNames = Constants.CACHE_NAME_PRODUCT, key = "#id"),
            @CacheEvict(cacheNames = Constants.CACHE_NAME_PRODUCT_LIST, allEntries = true)
    })
    public void addSkus(Long id, SkuDTO skuDTO) {
        ProductSku productSku = new ProductSku();
        BeanUtils.copyProperties(skuDTO,productSku);
        productSku.setCreatedAt(LocalDateTime.now());
        productSku.setProductId(id);
        productSkuMapper.insert(productSku);
        // 同步 ES：加 SKU 后价格区间/销量变化，重新灌这条商品
        syncEsQuietly(id);
    }


}
