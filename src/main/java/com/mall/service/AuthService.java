package com.mall.service;

import com.mall.dto.LoginRequest;
import com.mall.dto.LoginResponse;
import com.mall.dto.RegisterRequest;
import com.mall.dto.RegisterResponse;

/**
 * 认证服务：注册、登录
 *
 * @author 乐乐
 */
public interface AuthService {

    /**
     * 注册：成功后直接签发 token（注册即登录），无需再次登录
     */
    RegisterResponse register(RegisterRequest request);

    /**
     * 登录：认证成功后签发 token，写入 Redis 白名单
     */
    LoginResponse login(LoginRequest request);

    /**
     * 管理员登录：认证通过后额外校验是否具备 ADMIN 角色，
     * 非管理员调用后台登录入口时拒绝，避免普通用户从 /admin/auth/login 拿到 token。
     */
    LoginResponse adminLogin(LoginRequest request);
}
