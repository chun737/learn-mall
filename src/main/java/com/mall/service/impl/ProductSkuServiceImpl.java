package com.mall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mall.common.BusinessException;
import com.mall.common.Constants;
import com.mall.dto.SkuDTO;
import com.mall.entity.Product;
import com.mall.entity.ProductSku;
import com.mall.entity.StockLog;
import com.mall.enums.ErrorCode;
import com.mall.mapper.ProductMapper;
import com.mall.mapper.ProductSkuMapper;
import com.mall.mapper.StockLogMapper;
import com.mall.service.IProductSkuService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static com.mall.enums.ErrorCode.NOT_FOUND;
import static com.mall.enums.ErrorCode.STOCK_NOT_ENOUGH;

/**
 * <p>
 * 商品 SKU 表（规格/价格/库存） 服务实现类
 * </p>
 *
 * @author 乐乐
 * @since 2026-08-16
 */
@Service
public class ProductSkuServiceImpl extends ServiceImpl<ProductSkuMapper, ProductSku> implements IProductSkuService {
    private final ProductSkuMapper productSkuMapper;
    private final ProductMapper productMapper;
    private final StockLogMapper stockLogMapper;

    public ProductSkuServiceImpl(ProductSkuMapper productSkuMapper,
                                 ProductMapper productMapper,
                                 StockLogMapper stockLogMapper) {
        this.productSkuMapper = productSkuMapper;
        this.productMapper = productMapper;
        this.stockLogMapper = stockLogMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @Caching(evict = {
            // 入参 id 是 skuId，注解层拿不到所属 productId，只能整表逐出（管理端低频操作）
            @CacheEvict(cacheNames = "product", allEntries = true),
            @CacheEvict(cacheNames = "productList", allEntries = true)
    })
    public void modifySku(Long id, SkuDTO skuDTO) {
        // 1. 校验目标 SKU 存在且未删除
        ProductSku productSku = productSkuMapper.selectById(id);
        if (productSku == null || Constants.DELETED == productSku.getDeleted()) {
            throw new BusinessException(NOT_FOUND);
        }

        // 2. SKU 编码唯一校验：查出同名 SKU，排除自身（id 不同才算冲突）
        if (skuDTO.getSkuCode() != null && !skuDTO.getSkuCode().equals(productSku.getSkuCode())) {
            Long dupCount = productSkuMapper.selectCount(
                    new LambdaQueryWrapper<ProductSku>()
                            .eq(ProductSku::getSkuCode, skuDTO.getSkuCode())
                            .eq(ProductSku::getDeleted, Constants.NOT_DELETED)
                            .ne(ProductSku::getId, id));
            if (dupCount != null && dupCount > 0) {
                throw new BusinessException(ErrorCode.SKU_CODE_EXIST);
            }
        }

        // 3. 白名单更新允许修改的字段（只更新传了值的字段，避免覆盖成 null）
        if (skuDTO.getSkuCode() != null)    productSku.setSkuCode(skuDTO.getSkuCode());
        if (skuDTO.getSpecs() != null)      productSku.setSpecs(skuDTO.getSpecs());
        if (skuDTO.getPrice() != null)      productSku.setPrice(skuDTO.getPrice());
        if (skuDTO.getCostPrice() != null)  productSku.setCostPrice(skuDTO.getCostPrice());
        if (skuDTO.getStock() != null)      productSku.setStock(skuDTO.getStock());
        if (skuDTO.getImage() != null)      productSku.setImage(skuDTO.getImage());
        if (skuDTO.getStatus() != null)     productSku.setStatus(skuDTO.getStatus());
        productSku.setUpdatedAt(LocalDateTime.now());

        // 4. 更新落库（updateById 只更新非 null 字段，createdAt 等原值保留）
        productSkuMapper.updateById(productSku);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    // 库存只出现在商品详情里，列表 VO 无库存字段，逐出 product 即可
    @CacheEvict(cacheNames = "product", allEntries = true)
    public Integer adjustStock(Long id, Integer changeQty, String remark) {
        // 1. 参数校验
        if (changeQty == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }

        // 2. 校验 SKU 存在且未删除
        ProductSku sku = productSkuMapper.selectById(id);
        if (sku == null || Constants.DELETED == sku.getDeleted()) {
            throw new BusinessException(NOT_FOUND);
        }

        // 3. 记录变更前库存（写流水用）
        int beforeStock = sku.getStock() == null ? 0 : sku.getStock();

        // 4. 条件扣减库存：stock + changeQty >= 0 才执行（防负库存）
        int rows = productSkuMapper.adjustStock(id, changeQty);
        if (rows == 0) {
            throw new BusinessException(STOCK_NOT_ENOUGH);
        }

        // 5. 写库存流水（人工调整 change_type=4）
        StockLog log = new StockLog();
        log.setSkuId(id);
        log.setChangeType(Constants.STOCK_CHANGE_MANUAL);
        log.setChangeQty(changeQty);
        log.setBeforeStock(beforeStock);
        log.setAfterStock(beforeStock + changeQty);
        log.setRemark(remark);
        log.setCreatedAt(LocalDateTime.now());
        stockLogMapper.insert(log);

        // 6. 返回调整后库存
        return beforeStock + changeQty;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @Caching(evict = {
            @CacheEvict(cacheNames = "product", allEntries = true),
            // SKU 上下架改变列表页的 minPrice/maxPrice 聚合区间，列表也要失效
            @CacheEvict(cacheNames = "productList", allEntries = true)
    })
    public void modifySkuStatus(Long id, Integer status) {
        // 1. 状态合法性校验（0=下架 1=上架）
        if (status == null || (status != Constants.PRODUCT_STATUS_OFF_SHELF
                && status != Constants.PRODUCT_STATUS_ON_SHELF)) {
            throw new BusinessException(ErrorCode.PRODUCT_STATUS_ILLEGAL);
        }

        // 2. 校验 SKU 存在且未删除
        ProductSku sku = productSkuMapper.selectById(id);
        if (sku == null || Constants.DELETED == sku.getDeleted()) {
            throw new BusinessException(NOT_FOUND);
        }

        // 3. 上架前校验：所属商品 SPU 必须为上架状态
        if (status == Constants.PRODUCT_STATUS_ON_SHELF) {
            Product product = productMapper.selectById(sku.getProductId());
            if (product == null || Constants.DELETED == product.getDeleted()
                    || Constants.PRODUCT_STATUS_ON_SHELF != product.getStatus()) {
                throw new BusinessException(ErrorCode.PRODUCT_NOT_ON_SHELF);
            }
        }

        // 4. 更新 SKU 状态
        sku.setStatus(status);
        sku.setUpdatedAt(LocalDateTime.now());
        productSkuMapper.updateById(sku);
    }
}
