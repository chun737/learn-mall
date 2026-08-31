package com.mall.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * SKU 视图对象（前台商品详情）
 *
 * @author 乐乐
 */
@Data
public class SkuVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** SKU ID */
    private Long id;

    /** SKU 编码 */
    private String skuCode;

    /** 规格描述 */
    private String specs;

    /** 售价 */
    private BigDecimal price;

    /** 当前库存 */
    private Integer stock;

    /** 累计销量 */
    private Integer sales;

    /** SKU 图 URL */
    private String image;

}
