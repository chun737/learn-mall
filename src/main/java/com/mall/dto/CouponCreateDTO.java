package com.mall.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 创建优惠券请求（后台 4.7.2）
 *
 * @author 乐乐
 */
@Data
public class CouponCreateDTO {

    /** 券名称 */
    private String couponName;

    /** 类型：1=满减券 2=无门槛券（3=折扣券为扩展，暂不开放） */
    private Integer type;

    /** 使用门槛（满 X 元可用），type=1 时必填且 > 0；type=2 固定为 0 */
    private BigDecimal thresholdAmount;

    /** 抵扣金额，满减券须小于 thresholdAmount */
    private BigDecimal discountAmount;

    /** 发行总量，> 0 */
    private Integer totalCount;

    /** 每人限领张数，默认 1 */
    private Integer perLimit;

    /** 领取开始时间 */
    private LocalDateTime receiveStartTime;

    /** 领取结束时间（须晚于开始时间） */
    private LocalDateTime receiveEndTime;

    /** 领取后 N 天有效，> 0 */
    private Integer validDays;
}
