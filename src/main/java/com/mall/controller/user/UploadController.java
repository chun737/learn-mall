package com.mall.controller.user;

import com.mall.common.Result;
import com.mall.service.IOssService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * C 端用户上传：当前仅头像场景（/admin/upload 需要 ADMIN 角色，普通用户改头像必须走这里）。
 * 登录即可调用（/user/** 已被 Security 拦截要求认证）。
 */
@RestController("UserUploadController")
@RequestMapping("/user/upload")
@Tag(name = "用户上传")
public class UploadController {

    private final IOssService ossService;

    public UploadController(IOssService ossService) {
        this.ossService = ossService;
    }

    @PostMapping("/image")
    @Operation(summary = "上传图片（用户端，如头像），返回图片完整 URL")
    public Result<String> uploadImage(@RequestParam("file") MultipartFile file) {
        return Result.success(ossService.uploadImage(file));
    }
}
