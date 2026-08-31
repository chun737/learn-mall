package com.mall.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改密码请求（走请求体：密码不出现在 URL 中，避免落入访问日志/浏览器历史/代理记录）
 *
 * @author 乐乐
 */
@Data
public class ChangePasswordDTO {

    /** 原密码 */
    @NotBlank(message = "原密码不能为空")
    private String oldPassword;

    /** 新密码 */
    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 32, message = "新密码长度须为 6~32 位")
    private String newPassword;
}
