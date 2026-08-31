package com.mall.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * SKU 新增/修改请求
 *
 * @author 乐乐
 */
@Data
public class SkuDTO {

    /** SKU 编码（全局唯一） */
    private String skuCode;

    /** 规格描述，如 "256G 蓝色" */
    private String specs;

    /** 销售价 */
    private BigDecimal price;

    /** 成本价（毛利统计） */
    private BigDecimal costPrice;

    /** 初始库存 */
    private Integer stock;

    /** SKU 图片 URL */
    private String image;

    /** 状态：0=下架 1=上架，默认 1 */
    private Integer status;
}
