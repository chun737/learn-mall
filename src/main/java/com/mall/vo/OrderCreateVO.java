package com.mall.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 创建订单响应视图对象
 *
 * @author 乐乐
 */
@Data
public class OrderCreateVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 订单号 */
    private String orderNo;

    /** 订单 ID */
    private Long orderId;

    /** 订单状态（0 待支付） */
    private Integer orderStatus;

    /** 应付金额 */
    private BigDecimal payAmount;

    /** 下单时间 */
    private LocalDateTime createdAt;

}
