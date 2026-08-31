package com.mall.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * SKU 视图对象（后台）
 *
 * @author 乐乐
 */
@Data
public class AdminSkuVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** SKU ID */
    private Long id;

    /** 所属商品 SPU ID */
    private Long productId;

    /** SKU 编码 */
    private String skuCode;

    /** 规格描述 */
    private String specs;

    /** 销售价 */
    private BigDecimal price;

    /** 成本价 */
    private BigDecimal costPrice;

    /** 当前库存 */
    private Integer stock;

    /** 累计销量 */
    private Integer sales;

    /** SKU 图 URL */
    private String image;

    /** SKU 状态：0=下架 1=上架 */
    private Integer status;

}
