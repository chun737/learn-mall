package com.mall.controller.user;

import com.mall.common.Result;
import com.mall.dto.LoginRequest;
import com.mall.dto.LoginResponse;
import com.mall.dto.RegisterRequest;
import com.mall.dto.RegisterResponse;
import com.mall.service.AuthService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口：注册、登录
 *
 * @author 乐乐
 */
@RestController("UserAuthController")
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 注册（注册即登录，直接返回 token）
     */
    @PostMapping("/register")
    public Result<RegisterResponse> register(@RequestBody RegisterRequest request) {
        RegisterResponse response = authService.register(request);
        return Result.success(response);
    }

    /**
     * 登录
     */
    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return Result.success(response);
    }
}
