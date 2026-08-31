package com.mall.service.impl;

import com.mall.service.PayChannelService;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import java.math.BigDecimal;

/**
 * 模拟支付渠道：生成一个模拟收银台链接，不产生真实资金
 *
 * <p>切换真实支付宝沙箱时：新建 AlipayChannelServiceImpl 实现同一接口，
 * 用 @Primary 或替换本类即可，业务层无需改动。</p>
 *
 * @author 乐乐
 */
@Service
public class MockPayChannelServiceImpl implements PayChannelService {

    @Override
    public String createPayParams(String paymentNo, BigDecimal amount, String subject) {
        // 返回一个模拟收银台页面链接（前端跳转到此页面，模拟支付流程）
        // 真实场景这里应该返回支付宝 SDK 生成的 form 表单
        // URL 参数必须编码：中文 subject 不编码会导致收银台页面乱码/参数截断
        return "/mock-pay.html?paymentNo=" + URLEncoder.encode(paymentNo, StandardCharsets.UTF_8)
                + "&amount=" + amount
                + "&subject=" + URLEncoder.encode(subject, StandardCharsets.UTF_8);
    }
}
