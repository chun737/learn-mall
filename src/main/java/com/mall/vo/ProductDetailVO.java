package com.mall.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 商品详情视图对象（前台）
 *
 * @author 乐乐
 */
@Data
public class ProductDetailVO implements Serializable {

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

    /** 图文详情（HTML/JSON 文本） */
    private String detail;

    /** 上架 SKU 列表 */
    private List<SkuVO> skus;

}
