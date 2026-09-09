package com.mall.controller.admin;

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
 * 管理端文件上传：前端 multipart/form-data 提交，字段名 file，
 * 返回图片完整 URL，前端回填给商品主图 / SKU 图 / 头像等字段。
 */
@RestController("AdminUploadController")
@RequestMapping("/admin/upload")
@Tag(name = "文件上传")
public class UploadController {

    private final IOssService ossService;

    public UploadController(IOssService ossService) {
        this.ossService = ossService;
    }

    @PostMapping("/image")
    @Operation(summary = "上传图片（管理端），返回图片完整 URL")
    public Result<String> uploadImage(@RequestParam("file") MultipartFile file) {
        return Result.success(ossService.uploadImage(file));
    }
}
