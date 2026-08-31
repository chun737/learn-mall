package com.mall.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 下单预览商品清单项视图对象
 *
 * @author 乐乐
 */
@Data
public class OrderPreviewItemVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 购物车项 ID */
    private Long cartId;

    /** 商品 SPU ID */
    private Long productId;

    /** 商品名称 */
    private String productName;

    /** 商品主图 URL */
    private String mainImage;

    /** SKU ID */
    private Long skuId;

    /** 规格描述 */
    private String specs;

    /** 实时售价 */
    private BigDecimal price;

    /** 数量 */
    private Integer quantity;

    /** 当前库存 */
    private Integer stock;

}
