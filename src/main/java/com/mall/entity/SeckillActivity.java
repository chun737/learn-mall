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
 * 秒杀活动表
 * </p>
 *
 * @author 乐乐
 * @since 2026-08-27
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("seckill_activity")
public class SeckillActivity implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 秒杀活动 ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 活动名称
     */
    @TableField("activity_name")
    private String activityName;

    /**
     * 商品 SPU ID
     */
    @TableField("product_id")
    private Long productId;

    /**
     * 秒杀 SKU ID
     */
    @TableField("sku_id")
    private Long skuId;

    /**
     * 秒杀价
     */
    @TableField("seckill_price")
    private BigDecimal seckillPrice;

    /**
     * 秒杀总库存
     */
    @TableField("total_stock")
    private Integer totalStock;

    /**
     * 剩余秒杀库存（DB 条件扣减兜底防超卖）
     */
    @TableField("available_stock")
    private Integer availableStock;

    /**
     * 每人限购数量
     */
    @TableField("per_limit")
    private Integer perLimit;

    /**
     * 开始时间
     */
    @TableField("start_time")
    private LocalDateTime startTime;

    /**
     * 结束时间
     */
    @TableField("end_time")
    private LocalDateTime endTime;

    /**
     * 状态：0=未开始 1=进行中 2=已结束
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
     * 逻辑删除：0=正常 1=已删除
     */
    @TableField("deleted")
    private Integer deleted;


}
