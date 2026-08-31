package com.mall.entity;

import com.baomidou.mybatisplus.annotation.TableField;
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
 * 购物车表
 * </p>
 *
 * @author 乐乐
 * @since 2026-08-16
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("cart")
public class Cart implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 购物车项ID（主键）
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID（逻辑外键 -> user.id）
     */
    @TableField("user_id")
    private Long userId;

    /**
     * 商品ID（逻辑外键 -> product.id）
     */
    @TableField("product_id")
    private Long productId;

    /**
     * SKU ID（逻辑外键 -> product_sku.id）
     */
    @TableField("sku_id")
    private Long skuId;

    /**
     * 加入数量
     */
    @TableField("quantity")
    private Integer quantity;

    /**
     * 勾选状态：0=未勾选 1=已勾选（结算时只统计已勾选项）
     */
    @TableField("checked")
    private Integer checked;

    /**
     * 加入时间
     */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @TableField("updated_at")
    private LocalDateTime updatedAt;


}
