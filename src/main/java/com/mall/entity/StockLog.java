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
 * 库存扣减/回补流水表
 * </p>
 *
 * @author 乐乐
 * @since 2026-08-16
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("stock_log")
public class StockLog implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 库存流水ID（主键）
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * SKU ID（逻辑外键 -> product_sku.id）
     */
    @TableField("sku_id")
    private Long skuId;

    /**
     * 关联订单ID（扣减/回补来源，可为空表示手工调整）
     */
    @TableField("order_id")
    private Long orderId;

    /**
     * 关联订单号（冗余）
     */
    @TableField("order_no")
    private String orderNo;

    /**
     * 变更类型：1=下单扣减 2=取消回补 3=退款回补 4=人工调整
     */
    @TableField("change_type")
    private Integer changeType;

    /**
     * 变更数量（扣减为负数，回补为正数）
     */
    @TableField("change_qty")
    private Integer changeQty;

    /**
     * 变更前库存（对账用）
     */
    @TableField("before_stock")
    private Integer beforeStock;

    /**
     * 变更后库存（对账用）
     */
    @TableField("after_stock")
    private Integer afterStock;

    /**
     * 备注
     */
    @TableField("remark")
    private String remark;

    /**
     * 创建时间
     */
    @TableField("created_at")
    private LocalDateTime createdAt;


}
