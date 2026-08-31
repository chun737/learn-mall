package com.mall.dto;

import lombok.Data;

import java.util.List;

/**
 * 商品（SPU）新增/修改请求
 *
 * @author 乐乐
 */
@Data
public class ProductDTO {

    /** 所属分类 ID */
    private Long categoryId;

    /** 商品名称 */
    private String productName;

    /** 副标题/卖点 */
    private String subTitle;

    /** 主图 URL */
    private String mainImage;

    /** 详情富文本（HTML/JSON） */
    private String detail;

    /** 状态：0=下架 1=上架 2=草稿，默认 2 */
    private Integer status;

    /** 可选：一步创建 SKU 列表 */
    private List<SkuDTO> skus;
}
