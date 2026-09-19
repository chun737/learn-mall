package com.mall.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单详情视图对象
 *
 * @author 乐乐
 */
@Data
public class OrderDetailVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 订单号 */
    private String orderNo;

    /** 订单状态 */
    private Integer orderStatus;

    /** 订单状态文本 */
    private String orderStatusText;

    /** 支付状态 */
    private Integer paymentStatus;

    /** 商品总金额 */
    private BigDecimal totalAmount;

    /** 运费 */
    private BigDecimal freightAmount;

    /** 应付金额 */
    private BigDecimal payAmount;

    /** 优惠券优惠金额（未用券为 0） */
    private BigDecimal discountAmount;

    /** 订单备注 */
    private String remark;

    /** 收货信息快照 */
    private OrderReceiverVO receiver;

    /** 订单明细列表 */
    private List<OrderItemVO> items;

    /** 支付信息，未支付单可为 null */
    private PaymentVO payment;

    /** 支付时间 */
    private LocalDateTime paidAt;

    /** 发货时间 */
    private LocalDateTime shippedAt;

    /** 完成时间 */
    private LocalDateTime completedAt;

    /** 下单时间 */
    private LocalDateTime createdAt;

}
