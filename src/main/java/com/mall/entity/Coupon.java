package com.mall.entity;

import java.math.BigDecimal;

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
 * 优惠券模板表
 * </p>
 *
 * @author 乐乐
 * @since 2026-08-27
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("coupon")
public class Coupon implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 优惠券模板 ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 券名称
     */
    @TableField("coupon_name")
    private String couponName;

    /**
     * 类型：1=满减券 2=无门槛券 3=折扣券（扩展）
     */
    @TableField("type")
    private Integer type;

    /**
     * 使用门槛（满 X 元可用），无门槛券固定 0
     */
    @TableField("threshold_amount")
    private BigDecimal thresholdAmount;

    /**
     * 抵扣金额
     */
    @TableField("discount_amount")
    private BigDecimal discountAmount;

    /**
     * 折扣率（type=3 扩展用，如 0.85）
     */
    @TableField("discount_rate")
    private BigDecimal discountRate;

    /**
     * 发行总量
     */
    @TableField("total_count")
    private Integer totalCount;

    /**
     * 剩余可领数量（领券条件扣减防超发）
     */
    @TableField("remain_count")
    private Integer remainCount;

    /**
     * 每人限领张数
     */
    @TableField("per_limit")
    private Integer perLimit;

    /**
     * 领取开始时间
     */
    @TableField("receive_start_time")
    private LocalDateTime receiveStartTime;

    /**
     * 领取结束时间
     */
    @TableField("receive_end_time")
    private LocalDateTime receiveEndTime;

    /**
     * 领取后 N 天内有效
     */
    @TableField("valid_days")
    private Integer validDays;

    /**
     * 状态：0=停用 1=启用
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
    @TableLogic
    @TableField("deleted")
    private Integer deleted;

    /**
     * 适用范围：0=全场 1=指定品类 2=指定商品
     */
    @TableField("scope_type")
    private Integer scopeType;


}
