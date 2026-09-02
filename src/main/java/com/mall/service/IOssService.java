package com.mall.service;

import org.springframework.web.multipart.MultipartFile;

public interface IOssService {
    String uploadImage(MultipartFile file)  ; // 上传，返回完整 URL
    void delete(String objectName)          ;// 按对象名删（可选，先实现上传也行）

}
