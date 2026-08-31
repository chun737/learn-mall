package com.mall.controller.admin;

import com.alipay.v3.model.IsvAuthSceneInfo;
import com.mall.common.Result;
import com.mall.dto.LoginRequest;
import com.mall.dto.LoginResponse;
import com.mall.dto.RegisterRequest;
import com.mall.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController("AdminAuthController")
@RequestMapping("/admin")
@Tag(name = "管理员登录")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }
    @PostMapping("/auth/login")
    @Operation(summary = "管理员登录")
    public Result<LoginResponse> login(@RequestBody LoginRequest request){
        LoginResponse login = authService.adminLogin(request);
        return Result.success(login);
    }
}
