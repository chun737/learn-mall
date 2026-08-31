package com.mall.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 优惠券管理视图对象（后台列表 4.7.1）
 *
 * @author 乐乐
 */
@Data
public class CouponAdminVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 优惠券模板 ID */
    private Long id;

    /** 券名称 */
    private String couponName;

    /** 类型：1=满减券 2=无门槛券 3=折扣券（扩展） */
    private Integer type;

    /** 类型文本 */
    private String typeText;

    /** 使用门槛 */
    private BigDecimal thresholdAmount;

    /** 抵扣金额 */
    private BigDecimal discountAmount;

    /** 发行总量 */
    private Integer totalCount;

    /** 已领取数量（total_count - remain_count） */
    private Integer receivedCount;

    /** 已核销数量（user_coupon 聚合） */
    private Integer usedCount;

    /** 每人限领 */
    private Integer perLimit;

    /** 领取开始时间 */
    private LocalDateTime receiveStartTime;

    /** 领取结束时间 */
    private LocalDateTime receiveEndTime;

    /** 领取后 N 天有效 */
    private Integer validDays;

    /** 状态：0=停用 1=启用 */
    private Integer status;

    /** 状态文本 */
    private String statusText;
}
