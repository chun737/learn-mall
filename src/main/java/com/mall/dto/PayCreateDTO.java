package com.mall.dto;

import lombok.Data;

/**
 * 发起支付请求
 *
 * @author 乐乐
 */
@Data
public class PayCreateDTO {

    /** 待支付订单号 */
    private String orderNo;

    /** 支付方式：1=支付宝 2=微信 3=银行卡，默认 1 */
    private Integer payType;

    /** 支付完成同步跳转地址（前端页面） */
    private String returnUrl;
}
