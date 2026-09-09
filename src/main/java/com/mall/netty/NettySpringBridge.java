package com.mall.netty;

import com.mall.util.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class NettySpringBridge {
    @Resource
    private JwtUtil jwtUtil;                 // Spring 注入（demo1 已有）

    public static JwtUtil jwt;               // Netty 代码从这里取

    @PostConstruct                           // Spring 创建完这个 Bean 后自动执行
    public void init() {
        NettySpringBridge.jwt = jwtUtil;
    }

    /** 从 token 解出 userId；无效/过期返回 null。返回值 [0]=userId, [1]=角色字符串 */
    public static Object[] parseUser(String token) {
        try {
            Claims claims = jwt.parseToken(token);
            Long userId = Long.valueOf(claims.getSubject());
            List<?> roles = claims.get("roles", List.class);
            String role = (roles != null && !roles.isEmpty()) ? String.valueOf(roles.get(0)) : "USER";
            return new Object[]{userId, role};
        } catch (Exception e) {
            return null;                      // token 无效、过期、格式错，都当"没登录"
        }
    }
}
