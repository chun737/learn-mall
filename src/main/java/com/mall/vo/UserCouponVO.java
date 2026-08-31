package com.mall.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 我的优惠券视图对象（前台 3.6.3）
 *
 * @author 乐乐
 */
@Data
public class UserCouponVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户优惠券记录 ID */
    private Long userCouponId;

    /** 优惠券模板 ID */
    private Long couponId;

    /** 优惠券名称 */
    private String couponName;

    /** 类型：1=满减券 2=无门槛券 3=折扣券（扩展） */
    private Integer type;

    /** 使用门槛 */
    private BigDecimal thresholdAmount;

    /** 抵扣金额 */
    private BigDecimal discountAmount;

    /** 持券状态：0=未使用 1=已使用 2=已过期 */
    private Integer couponStatus;

    /** 状态文本 */
    private String couponStatusText;

    /** 领取时间 */
    private LocalDateTime receiveTime;

    /** 过期时间 */
    private LocalDateTime expireTime;

    /** 核销时间，未使用为 null */
    private LocalDateTime useTime;

    /** 核销订单号，未使用为 null */
    private String orderNo;
}
