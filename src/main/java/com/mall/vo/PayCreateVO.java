package com.mall.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 发起支付响应视图对象
 *
 * @author 乐乐
 */
@Data
public class PayCreateVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 支付单号 */
    private String paymentNo;

    /** 订单号 */
    private String orderNo;

    /** 支付金额 */
    private BigDecimal amount;

    /** 支付方式：1=支付宝 2=微信 3=银行卡 */
    private Integer payType;

    /** 支付状态：0=待支付 */
    private Integer payStatus;

    /** 第三方支付参数 */
    private PayParamsVO payParams;

}
