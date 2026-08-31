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
 * 用户持券表
 * </p>
 *
 * @author 乐乐
 * @since 2026-08-27
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("user_coupon")
public class UserCoupon implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 用户优惠券记录 ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 优惠券模板 ID
     */
    @TableField("coupon_id")
    private Long couponId;

    /**
     * 领取用户 ID
     */
    @TableField("user_id")
    private Long userId;

    /**
     * 状态：0=未使用 1=已使用 2=已过期
     */
    @TableField("coupon_status")
    private Integer couponStatus;

    /**
     * 领取时间
     */
    @TableField("receive_time")
    private LocalDateTime receiveTime;

    /**
     * 过期时间 = receive_time + valid_days
     */
    @TableField("expire_time")
    private LocalDateTime expireTime;

    /**
     * 核销时间
     */
    @TableField("use_time")
    private LocalDateTime useTime;

    /**
     * 核销订单号（退券时按此定位返还）
     */
    @TableField("order_no")
    private String orderNo;

    /**
     * 创建时间
     */
    @TableField("created_at")
    private LocalDateTime createdAt;


}
