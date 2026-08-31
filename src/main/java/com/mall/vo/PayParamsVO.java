package com.mall.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 第三方支付参数视图对象
 *
 * @author 乐乐
 */
@Data
public class PayParamsVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 支付宝页面支付跳转地址 */
    private String alipayTradePagePay;

    /** 微信支付参数 */
    private String wxPay;

}
