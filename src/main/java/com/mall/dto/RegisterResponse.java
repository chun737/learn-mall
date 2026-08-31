package com.mall.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 注册响应：注册即登录，直接返回 token
 *
 * @author 乐乐
 */
@Data
@AllArgsConstructor
public class RegisterResponse {

    /** 用户 ID */
    private Long id;

    /** 用户名 */
    private String username;

    /** 昵称 */
    private String nickname;

    /** 注册成功后签发的 token（注册即登录） */
    private String token;
}
