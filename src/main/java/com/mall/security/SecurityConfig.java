package com.mall.security;


import com.mall.common.Constants;
import com.mall.common.Result;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

/**
 * Spring Security 配置类
 *
 * @author 乐乐
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    /**
     * 密码加密器
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * AuthenticationManager（供手动登录认证使用）
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    /**
     * 安全过滤链
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 关闭 CSRF（前后端分离 + JWT 无状态，不需要）
                .csrf(csrf -> csrf.disable())
                // 无状态会话，不用 session 存登录态
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 授权规则
                .authorizeHttpRequests(auth -> auth
                        // 公开接口白名单
                        .requestMatchers(
                                "/auth/register",
                                "/auth/login",
                                "/admin/auth/login",
                                "/error",
                                "/user/password/forget"
                        ).permitAll()
                        // 商品浏览、分类等只读接口公开（仅 GET 方法）
                        .requestMatchers(org.springframework.http.HttpMethod.GET,
                                "/product/**",
                                "/categories/**",
                                "/product",
                                "/categories",
                                "/seckill/**"
                        ).permitAll()
                        // 后台接口需要 ADMIN 角色
                        .requestMatchers("/admin/**").hasRole(Constants.ROLE_ADMIN)
                        // 其余接口需要登录
                        .anyRequest().authenticated()
                )
                // 将 JWT 过滤器放在用户名密码过滤器之前
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                // 未认证 / 无权限时的 JSON 响应
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, e) ->
                                writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, 401, "未登录或登录已过期"))
                        .accessDeniedHandler((request, response, e) ->
                                writeJson(response, HttpServletResponse.SC_FORBIDDEN, 403, "无权限访问"))
                );

        return http.build();
    }

    /**
     * 统一输出 JSON 格式的错误响应
     */
    private void writeJson(HttpServletResponse response, int httpStatus, int code, String message) throws java.io.IOException {
        response.setStatus(httpStatus);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(Result.error(code, message)));
    }
}
