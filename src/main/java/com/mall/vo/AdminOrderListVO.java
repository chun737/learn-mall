package com.mall.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单列表项视图对象（后台）
 *
 * @author 乐乐
 */
@Data
public class AdminOrderListVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 订单号 */
    private String orderNo;

    /** 下单用户 ID */
    private Long userId;

    /** 收货人姓名 */
    private String receiverName;

    /** 收货人手机号 */
    private String receiverPhone;

    /** 订单状态 */
    private Integer orderStatus;

    /** 订单状态文本 */
    private String orderStatusText;

    /** 支付状态 */
    private Integer paymentStatus;

    /** 应付金额 */
    private BigDecimal payAmount;

    /** 明细条数 */
    private Integer itemCount;

    /** 下单时间 */
    private LocalDateTime createdAt;

}
