package com.mall.service.impl;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.ObjectMetadata;
import com.mall.common.BusinessException;
import com.mall.common.Constants;
import com.mall.config.OssProperties;
import com.mall.service.IOssService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import static com.mall.common.Constants.MAX_IMAGE_SIZE;
import static com.mall.enums.ErrorCode.PARAM_ERROR;
import static com.mall.enums.ErrorCode.SYSTEM_ERROR;

@Service
public class OssService implements IOssService {

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

    /**
     * 上传图片。四道校验，顺序为「先便宜后昂贵」：
     * <ol>
     *   <li>非空 + 大小上限（5MB）：不读内容，最便宜；</li>
     *   <li><b>文件头魔数识别真实格式</b>：改名攻击（把任意文件命名成 evil.png）在这一步被拒；</li>
     *   <li>扩展名白名单 <b>且与真实格式一致</b>：只认白名单内后缀，同时防"真 png 却叫 .php"这类不一致；</li>
     *   <li>显式写入 Content-Type：不依赖 OSS 按后缀推断，避免对象被当成可执行/可渲染的其它类型分发。</li>
     * </ol>
     *
     * <p><b>为什么必须以魔数为准</b>：原先只校验 {@code getOriginalFilename()} 的扩展名，
     * 而扩展名完全由客户端提供，改个名字就能上传任意二进制内容。魔数取自文件内容，客户端无法在
     * 不破坏图片结构的前提下伪造（JPEG/PNG/GIF/WebP/BMP 的文件头是格式规范强制的）。
     *
     * <p>整个文件只读一次并缓冲：5MB 上限下内存可接受，且避免"嗅探读一遍、上传再读一遍"的双倍开销；
     * {@link MultipartFile#getInputStream()} 在部分实现下不可重复读取，缓冲同时也消除了这个隐患。
     */
    @Override
    public String uploadImage(MultipartFile file) {
        // 1. 非空校验
        if (file == null || file.isEmpty()) {
            throw new BusinessException(PARAM_ERROR.getCode(), "请选择要上传的图片");
        }
        // 2. 大小校验（5MB）——放在读内容之前，避免为超大文件分配内存
        if (file.getSize() > MAX_IMAGE_SIZE) {
            throw new BusinessException(PARAM_ERROR.getCode(), "图片大小不能超过 5MB");
        }

        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            // 读流失败属服务端/网络问题，不是用户参数错误
            throw new BusinessException(SYSTEM_ERROR.getCode(), "图片读取失败，请重试");
        }
        if (content.length == 0) {
            throw new BusinessException(PARAM_ERROR.getCode(), "请选择要上传的图片");
        }

        // 3. 魔数识别真实格式（唯一可信的格式来源）
        String detectedExt = Constants.detectImageExt(content);
        if (detectedExt == null) {
            throw new BusinessException(PARAM_ERROR.getCode(),
                    "文件内容不是有效图片，仅支持 " + Constants.ALLOWED_IMAGE_EXT_TEXT + " 格式");
        }

        // 4. 扩展名必须在白名单内，且与真实内容一致（jpeg/jpg 视为同一格式）
        String declaredExt = Constants.normalizeImageExt(
                StringUtils.getFilenameExtension(file.getOriginalFilename()));
        if (declaredExt == null) {
            throw new BusinessException(PARAM_ERROR.getCode(),
                    "仅支持 " + Constants.ALLOWED_IMAGE_EXT_TEXT + " 格式");
        }
        if (!declaredExt.equals(detectedExt)) {
            throw new BusinessException(PARAM_ERROR.getCode(),
                    "文件后缀（." + declaredExt + "）与实际图片格式（" + detectedExt + "）不一致，请检查文件");
        }

        // 5. 生成对象名：images/yyyy/MM/dd/{uuid}.{ext}（日期分目录 + UUID 防重名，不使用客户端文件名）
        String dir = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String objectName = "images/" + dir + "/"
                + UUID.randomUUID().toString().replace("-", "") + "." + detectedExt;

        // 6. 上传：显式设置 Content-Type，不让 OSS 按后缀猜
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(content.length);
        metadata.setContentType(Constants.imageContentType(detectedExt));
        ossClient.putObject(properties.getBucketName(), objectName,
                new ByteArrayInputStream(content), metadata);
        // 7. 返回可访问 URL
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
