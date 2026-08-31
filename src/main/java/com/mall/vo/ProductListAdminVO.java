package com.mall.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 后台商品列表视图对象（SPU + SKU 聚合信息）
 *
 * @author 乐乐
 */
@Data
public class ProductListAdminVO implements Serializable {

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

    /** 库存合计（该 SPU 下所有 SKU 库存之和） */
    private Integer stockTotal;

    /** 状态：0=下架 1=上架 2=草稿 */
    private Integer status;

    /** 状态文本（前端展示用） */
    private String statusText;

    /** SKU 数量 */
    private Integer skuCount;

    /** 销量合计 */
    private Integer sales;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
