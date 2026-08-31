package com.mall.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 优惠券领取记录视图对象（后台 4.7.4）
 *
 * @author 乐乐
 */
@Data
public class CouponRecordVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 记录 ID */
    private Long userCouponId;

    /** 领取用户 ID */
    private Long userId;

    /** 用户名 */
    private String username;

    /** 持券状态：0=未使用 1=已使用 2=已过期 */
    private Integer couponStatus;

    /** 状态文本 */
    private String couponStatusText;

    /** 领取时间 */
    private LocalDateTime receiveTime;

    /** 核销时间 */
    private LocalDateTime useTime;

    /** 核销订单号 */
    private String orderNo;
}
