package com.mall.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@Data
@ConfigurationProperties(prefix = "oss")
public class OssProperties {
    private String endpoint;        // 对应 yaml 的 oss.endpoint
    private String accessKeyId;     // 对应 oss.access-key-id
    private String accessKeySecret; // 对应 oss.access-key-secret
    private String bucketName;      // 对应 oss.bucket-name
    private String urlPrefix;       // 对应 oss.url-prefix
}
