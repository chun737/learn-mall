package com.mall.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 商品列表项视图对象（前台）
 *
 * @author 乐乐
 */
@Data
public class ProductListVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 商品 SPU ID */
    private Long id;

    /** 分类 ID */
    private Long categoryId;

    /** 商品名称 */
    private String productName;

    /** 副标题/卖点 */
    private String subTitle;

    /** 主图 URL */
    private String mainImage;

    /** 该 SPU 下 SKU 最低售价 */
    private BigDecimal minPrice;

    /** 该 SPU 下 SKU 最高售价 */
    private BigDecimal maxPrice;

    /** 累计销量 */
    private Integer sales;

}
