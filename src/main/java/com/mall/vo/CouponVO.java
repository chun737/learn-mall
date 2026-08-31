package com.mall.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 优惠券视图对象（前台可领列表 3.6.1）
 *
 * @author 乐乐
 */
@Data
public class CouponVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 优惠券模板 ID */
    private Long id;

    /** 优惠券名称 */
    private String couponName;

    /** 类型：1=满减券 2=无门槛券 3=折扣券（扩展） */
    private Integer type;

    /** 类型文本 */
    private String typeText;

    /** 使用门槛（满 X 元可用），无门槛券为 0.00 */
    private BigDecimal thresholdAmount;

    /** 抵扣金额 */
    private BigDecimal discountAmount;

    /** 剩余可领数量，0 表示已领完（前端置灰按钮） */
    private Integer remainCount;

    /** 每人限领张数 */
    private Integer perLimit;

    /** 领取开始时间 */
    private LocalDateTime receiveStartTime;

    /** 领取结束时间 */
    private LocalDateTime receiveEndTime;

    /** 领取后 N 天内有效 */
    private Integer validDays;
}
