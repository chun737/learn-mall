package com.mall.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 支付信息视图对象
 *
 * @author 乐乐
 */
@Data
public class PaymentVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 支付单号 */
    private String paymentNo;

    /** 支付方式：1=支付宝 2=微信 3=银行卡 */
    private Integer payType;

    /** 支付状态：0=待支付 1=成功 2=失败 3=已退款 */
    private Integer payStatus;

    /** 支付时间 */
    private LocalDateTime paidAt;

}
