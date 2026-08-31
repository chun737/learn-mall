package com.mall.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 登录响应：返回 token 和用户信息
 *
 * @author 乐乐
 */
@Data
@AllArgsConstructor
public class LoginResponse {

    private String token;

    private Long userId;

    private String username;
}
