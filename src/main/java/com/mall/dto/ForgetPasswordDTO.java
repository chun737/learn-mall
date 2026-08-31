package com.mall.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 忘记密码请求
 *
 * @author 乐乐
 */
@Data
public class ForgetPasswordDTO {

    /** 用户名 */
    @NotBlank(message = "用户名不能为空")
    private String username;

    /** 注册时填写的手机号（用于身份验证） */
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    /** 新密码 */
    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 32, message = "新密码长度须为 6~32 位")
    private String newPassword;
}
