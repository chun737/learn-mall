package com.mall.entity;

import java.math.BigDecimal;

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
 * 订单明细表（商品快照）
 * </p>
 *
 * @author 乐乐
 * @since 2026-08-16
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("order_item")
public class OrderItem implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 订单明细ID（主键）
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 订单ID（逻辑外键 -> order.id）
     */
    @TableField("order_id")
    private Long orderId;

    /**
     * 订单号（冗余，便于按订单号反查明细）
     */
    @TableField("order_no")
    private String orderNo;

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
     * 商品名称（快照）
     */
    @TableField("product_name")
    private String productName;

    /**
     * SKU 规格（快照，如 "256G 蓝色"）
     */
    @TableField("sku_specs")
    private String skuSpecs;

    /**
     * SKU 图片（快照）
     */
    @TableField("sku_image")
    private String skuImage;

    /**
     * 成交单价（快照，下单时价格）
     */
    @TableField("price")
    private BigDecimal price;

    /**
     * 购买数量
     */
    @TableField("quantity")
    private Integer quantity;

    /**
     * 小计金额（price * quantity）
     */
    @TableField("total_amount")
    private BigDecimal totalAmount;

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


}
