package com.mall.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 商品列表项视图对象（后台）
 *
 * @author 乐乐
 */
@Data
public class AdminProductListVO implements Serializable {

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

    /** 商品状态：0=下架 1=上架 2=草稿 */
    private Integer status;

    /** 状态文本 */
    private String statusText;

    /** SKU 数量 */
    private Integer skuCount;

    /** 库存合计 */
    private Integer stockTotal;

    /** 累计销量 */
    private Integer sales;

    /** 创建时间 */
    private LocalDateTime createdAt;

}
