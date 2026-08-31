package com.mall.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * JWT 工具类：签发 / 解析 token
 * 密钥与有效期从 application.yaml 读取
 *
 * @author 乐乐
 */

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expire-time:604800000}")
    @Getter
    private long expireTime;

    /**
     * 生成 SecretKey（HS256 要求密钥长度 >= 32 字节）
     */
    private SecretKey getKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 签发 token
     *
     * @param userId   用户 ID
     * @param username 用户名
     * @param roles    角色编码列表（多角色，如 ["USER", "ADMIN"]）
     */
    public String createToken(Long userId, String username, List<String> roles) {
        Date now = new Date();
        Date expire = new Date(now.getTime() + expireTime);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("roles", roles)
                .issuedAt(now)
                .expiration(expire)
                .signWith(getKey())
                .compact();
    }

    /**
     * 解析 token，返回 Claims（无效或过期会抛异常）
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 校验 token 是否有效
     */
    public boolean validate(String token) {
        try {
            parseToken(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 获取用户 ID
     */
    public Long getUserId(String token) {
        return Long.valueOf(parseToken(token).getSubject());
    }

    /**
     * 获取用户名
     */
    public String getUsername(String token) {
        return parseToken(token).get("username", String.class);
    }

    /**
     * 获取角色列表（多角色）
     */
    public List<String> getRoles(String token) {
        List<?> roles = parseToken(token).get("roles", List.class);
        if (roles == null) {
            return Collections.emptyList();
        }
        return roles.stream().map(String::valueOf).collect(java.util.stream.Collectors.toList());
    }

    /**
     * 获取角色列表（Claims 重载）：供过滤器在已解析 Claims 的情况下复用，避免同一 token 二次解析
     */
    public List<String> getRoles(Claims claims) {
        List<?> roles = claims.get("roles", List.class);
        if (roles == null) {
            return Collections.emptyList();
        }
        return roles.stream().map(String::valueOf).collect(java.util.stream.Collectors.toList());
    }
}
