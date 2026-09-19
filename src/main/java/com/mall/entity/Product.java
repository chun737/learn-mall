package com.mall.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import java.time.LocalDateTime;
import java.io.Serializable;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * <p>
 * 商品表（SPU）
 * </p>
 *
 * @author 乐乐
 * @since 2026-08-16
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("product")
public class Product implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 商品ID（SPU，主键）
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属分类ID（逻辑外键 -> category.id）
     */
    @TableField("category_id")
    private Long categoryId;

    /**
     * 商品名称（SPU 名称，如"iPhone 15"）
     */
    @TableField("product_name")
    private String productName;

    /**
     * 商品副标题/卖点
     */
    @TableField("sub_title")
    private String subTitle;

    /**
     * 商品主图 URL
     */
    @TableField("main_image")
    private String mainImage;

    /**
     * 商品详情（富文本，可存 JSON/HTML）
     */
    @TableField("detail")
    private String detail;

    /**
     * 商品状态：0=下架 1=上架 2=草稿
     */
    @TableField("status")
    private Integer status;

    /**
     * 创建时间
     */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @TableField("updated_at")
    private LocalDateTime updatedAt;

    /**
     * 逻辑删除：0=未删除 1=已删除
     */
    @TableLogic
    @TableField("deleted")
    private Integer deleted;


}
