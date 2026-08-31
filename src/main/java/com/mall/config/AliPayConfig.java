package com.mall.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@Data
@ConfigurationProperties(prefix = "alipay")
public class AliPayConfig {
    private String appId;          // 沙箱 AppID
    private String gateway;        // 沙箱网关
    private String privateKey;     // 应用私钥
    private String alipayPublicKey; // 支付宝公钥
    private String notifyUrl;      // 异步回调地址
}
