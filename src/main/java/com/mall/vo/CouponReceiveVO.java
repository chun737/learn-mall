package com.mall.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 领取优惠券结果视图对象（前台 3.6.2）
 *
 * @author 乐乐
 */
@Data
public class CouponReceiveVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户优惠券记录 ID（下单传 couponId 即此 ID） */
    private Long userCouponId;

    /** 优惠券模板 ID */
    private Long couponId;

    /** 优惠券名称 */
    private String couponName;

    /** 持券状态：0=未使用 */
    private Integer couponStatus;

    /** 过期时间 */
    private LocalDateTime expireTime;
}
