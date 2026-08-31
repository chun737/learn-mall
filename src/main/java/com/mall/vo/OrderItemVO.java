package com.mall.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 订单明细视图对象
 *
 * @author 乐乐
 */
@Data
public class OrderItemVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 商品 SPU ID */
    private Long productId;

    /** 商品名称快照 */
    private String productName;

    /** SKU ID */
    private Long skuId;

    /** 规格快照 */
    private String skuSpecs;

    /** SKU 图快照 */
    private String skuImage;

    /** 成交单价快照 */
    private BigDecimal price;

    /** 数量 */
    private Integer quantity;

    /** 小计金额 */
    private BigDecimal totalAmount;

}
