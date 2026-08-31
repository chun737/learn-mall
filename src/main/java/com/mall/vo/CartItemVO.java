package com.mall.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 购物车项视图对象
 *
 * @author 乐乐
 */
@Data
public class CartItemVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 购物车项 ID */
    private Long id;

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

    /** SKU 图 URL */
    private String skuImage;

    /** SKU 实时售价 */
    private BigDecimal price;

    /** 数量 */
    private Integer quantity;

    /** 勾选状态：0=未勾选 1=已勾选 */
    private Integer checked;

    /** 当前库存 */
    private Integer stock;

}
