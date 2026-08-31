package com.mall.security;

import com.mall.common.Constants;
import com.mall.util.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * JWT 认证过滤器：从请求头解析 token，注入 SecurityContext
 *
 * @author 乐乐
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";
    private final StringRedisTemplate stringRedisTemplate;
    private final JwtUtil jwtUtil;

    public JwtAuthenticationFilter(JwtUtil jwtUtil
    ,StringRedisTemplate stringRedisTemplate) {
        this.jwtUtil = jwtUtil;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(HEADER);

        // 有 token 且尚未认证时才解析
        if (header != null && header.startsWith(PREFIX)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = header.substring(PREFIX.length());
            try {
                Claims claims = jwtUtil.parseToken(token);
                Long userId = Long.valueOf(claims.getSubject());
                // 先查白名单（强制下线的 token 在这里出局，只花一次解析），命中后才从同一 Claims 取角色
                String cachedToken = stringRedisTemplate.opsForValue().get(Constants.LOGIN_TOKEN_PREFIX + userId);
                List<String> roles = jwtUtil.getRoles(claims);
                if (cachedToken == null || !cachedToken.equals(token)) {
                    SecurityContextHolder.clearContext();
                    filterChain.doFilter(request, response);
                    return;
                }
                List<SimpleGrantedAuthority> authorities = new ArrayList<>(roles.size());
                for (String role : roles) {
                    authorities.add(new SimpleGrantedAuthority(Constants.ROLE_PREFIX + role));
                }
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                userId,
                                null,
                                authorities
                        );
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (Exception e) {
                // token 无效，不设置认证，交给后续授权规则决定是否放行
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }
}
