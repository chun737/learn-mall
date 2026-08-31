package com.mall.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付结果查询视图对象
 *
 * @author 乐乐
 */
@Data
public class PayResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 支付单号 */
    private String paymentNo;

    /** 订单号 */
    private String orderNo;

    /** 支付金额 */
    private BigDecimal amount;

    /** 支付方式：1=支付宝 2=微信 3=银行卡 */
    private Integer payType;

    /** 支付状态：0=待支付 1=成功 2=失败 3=已退款 */
    private Integer payStatus;

    /** 支付状态文本 */
    private String payStatusText;

    /** 第三方交易号 */
    private String thirdTradeNo;

    /** 支付时间 */
    private LocalDateTime paidAt;

}
