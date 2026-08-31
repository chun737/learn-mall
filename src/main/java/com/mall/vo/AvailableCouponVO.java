package com.mall.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 结算可用优惠券视图对象（前台 3.6.4）
 *
 * @author 乐乐
 */
@Data
public class AvailableCouponVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户优惠券记录 ID */
    private Long userCouponId;

    /** 优惠券名称 */
    private String couponName;

    /** 类型：1=满减券 2=无门槛券 */
    private Integer type;

    /** 使用门槛 */
    private BigDecimal thresholdAmount;

    /** 抵扣金额 */
    private BigDecimal discountAmount;

    /** 过期时间 */
    private LocalDateTime expireTime;

    /** 是否最优券：1=是（抵扣最大且过期时间最晚），全列表仅一条标记 */
    private Integer best;
}
