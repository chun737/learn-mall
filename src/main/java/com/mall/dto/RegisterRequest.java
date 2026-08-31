package com.mall.dto;

import lombok.Data;

/**
 * 注册请求
 *
 * @author 乐乐
 */
@Data
public class RegisterRequest {

    private String username;

    private String password;

    private String nickname;

    /** 手机号 */
    private String phone;

    /** 邮箱 */
    private String email;

    /** 性别：0=未知 1=男 2=女，默认 0 */
    private Integer gender;
}
