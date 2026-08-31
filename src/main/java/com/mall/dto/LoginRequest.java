package com.mall.dto;

import lombok.Data;

/**
 * 登录请求
 *
 * @author 乐乐
 */
@Data
public class LoginRequest {

    private String username;

    private String password;
}
