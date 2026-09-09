package com.mall.es;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.math.BigDecimal;

@Data
@Document(indexName = "product")
public class ProductDoc {
    @Id
    private Long id;

    /** 分类ID：过滤用 */
    @Field(type = FieldType.Long)
    private Long categoryId;

    /** 商品名：索引 ik_max_word（细切）、搜索 ik_smart（粗切） */
    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String productName;

    /** 副标题：分词 */
    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String subTitle;

    /** 主图：只展示，不搜索 */
    @Field(type = FieldType.Keyword)
    private String mainImage;

    /** 最低价：价格排序 */
    @Field(type = FieldType.Double)
    private BigDecimal minPrice;

    /** 最高价 */
    @Field(type = FieldType.Double)
    private BigDecimal maxPrice;

    /** 销量：销量排序 */
    @Field(type = FieldType.Integer)
    private Integer sales;
}
