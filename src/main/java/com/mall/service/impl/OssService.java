package com.mall.service.impl;

import com.aliyun.oss.OSS;
import com.mall.common.BusinessException;
import com.mall.config.OssProperties;
import com.mall.service.IOssService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import static com.mall.common.Constants.MAX_IMAGE_SIZE;
import static com.mall.enums.ErrorCode.PARAM_ERROR;
import static com.mall.enums.ErrorCode.SYSTEM_ERROR;

@Service
public class OssService implements IOssService {

    /** 图片扩展名白名单 */
    private static final List<String> ALLOWED_EXTS = List.of("jpg", "jpeg", "png", "gif", "webp", "bmp");

    private final OSS ossClient;
    private final OssProperties properties;

    public OssService(OSS ossClient, OssProperties properties) {
        this.ossClient = ossClient;
        this.properties = properties;
    }

    @Override
    public void delete(String objectName) {
        if (StringUtils.hasText(objectName)) {
            ossClient.deleteObject(properties.getBucketName(), objectName);
        }
    }

    @Override
    public String uploadImage(MultipartFile file) {
        // 1. 非空校验
        if (file == null || file.isEmpty()) {
            throw new BusinessException(PARAM_ERROR.getCode(), "请选择要上传的图片");
        }
        // 2. 大小校验（5MB）
        if (file.getSize() > MAX_IMAGE_SIZE) {
            throw new BusinessException(PARAM_ERROR.getCode(), "图片大小不能超过 5MB");
        }
        // 3. 扩展名白名单校验
        String ext = StringUtils.getFilenameExtension(file.getOriginalFilename());
        if (ext == null || !ALLOWED_EXTS.contains(ext.toLowerCase())) {
            throw new BusinessException(PARAM_ERROR.getCode(), "仅支持 jpg/png/gif/webp/bmp 格式");
        }
        // 4. 生成对象名：images/yyyy/MM/dd/{uuid}.{ext}（日期分目录 + UUID 防重名）
        String dir = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String objectName = "images/" + dir + "/"
                + UUID.randomUUID().toString().replace("-", "") + "." + ext.toLowerCase();

        // 5. 上传
        try (InputStream in = file.getInputStream()) {
            ossClient.putObject(properties.getBucketName(), objectName, in);
        } catch (IOException e) {
            throw new BusinessException(SYSTEM_ERROR);
        }
        // 6. 返回可访问 URL
        return resolveUrl(objectName);
    }

    /**
     * 拼可访问 URL：urlPrefix 非空用自定义域名，否则自动拼 https://{bucket}.{endpoint}/{objectName}
     */
    private String resolveUrl(String objectName) {
        if (StringUtils.hasText(properties.getUrlPrefix())) {
            return properties.getUrlPrefix().replaceAll("/+$", "") + "/" + objectName;
        }
        String endpoint = properties.getEndpoint();
        String scheme = endpoint.startsWith("https") ? "https" : "http";
        String host = endpoint.replaceAll("^https?://", "");
        return scheme + "://" + properties.getBucketName() + "." + host + "/" + objectName;
    }
}
