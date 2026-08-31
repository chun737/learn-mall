package com.mall.util;

import com.mall.common.Constants;
import com.mall.mapper.ProductMapper;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 商品 ID 布隆过滤器声明：只提供配置与数据源，机制在 BloomFilterRegistry。
 * 商品详情 getDPs（防穿透）使用；新增商品落库后须 registry.add(...)，否则新商品被误杀 404。
 *
 * @author 乐乐
 */
@Component
public class ProductBloomIndex implements BloomFilterSupport {

    private final ProductMapper productMapper;

    public ProductBloomIndex(ProductMapper productMapper) {
        this.productMapper = productMapper;
    }

    @Override
    public String filterName() {
        return Constants.BLOOM_FILTER_PRODUCT;
    }

    @Override
    public long expectedInsertions() {
        return Constants.BLOOM_EXPECTED_INSERTIONS;
    }

    @Override
    public List<Long> loadAllIds() {
        return productMapper.selectAllIds();   // XML SQL：全部未删除商品 ID
    }
}
