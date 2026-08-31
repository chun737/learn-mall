package com.mall.service;

import com.mall.dto.SkuDTO;
import com.mall.entity.ProductSku;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 商品 SKU 表（规格/价格/库存） 服务类
 * </p>
 *
 * @author 乐乐
 * @since 2026-08-16
 */
public interface IProductSkuService extends IService<ProductSku> {

    void modifySku(Long id, SkuDTO skuDTO);

    /**
     * 人工调整 SKU 库存（正数增加、负数减少），写库存流水
     *
     * @return 调整后的库存
     */
    Integer adjustStock(Long id, Integer changeQty, String remark);

    /**
     * SKU 上架/下架
     */
    void modifySkuStatus(Long id, Integer status);
}
