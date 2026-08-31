package com.mall.service;

import java.math.BigDecimal;

/**
 * 支付渠道抽象：模拟支付 / 真实沙箱支付统一入口
 *
 * <p>当前学习项目先用「模拟支付」实现跑通闭环，
 * 拿到支付宝沙箱密钥后，只需新增一个 AlipayChannelServiceImpl 替换实现即可。</p>
 *
 * @author 乐乐
 */
public interface PayChannelService {

    /**
     * 生成支付参数（form 表单 / 支付跳转链接）
     *
     * @param paymentNo 商户支付单号
     * @param amount    支付金额（元）
     * @param subject   支付标题
     * @return 前端可直接使用的支付参数
     */
    String createPayParams(String paymentNo, BigDecimal amount, String subject);
}
