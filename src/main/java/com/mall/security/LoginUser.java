package com.mall.security;

import com.mall.common.Constants;
import com.mall.entity.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * 登录认证主体，包装 User 实体，实现 Spring Security 的 UserDetails
 *
 * @author 乐乐
 */
public class LoginUser implements UserDetails {

    private final User user;

    public LoginUser(User user) {
        this.user = user;
    }

    public User getUser() {
        return user;
    }

    public Long getUserId() {
        return user.getId();
    }

    /**
     * 权限集合：一个用户可拥有多个角色，每个角色映射为一个 authority（前缀 ROLE_）
     * 从 user.roles（user_role + role 联查结果）动态生成
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        List<String> roles = user.getRoles();
        if (roles == null || roles.isEmpty()) {
            return Collections.singletonList(new SimpleGrantedAuthority(Constants.ROLE_PREFIX + Constants.ROLE_USER));
        }
        List<GrantedAuthority> authorities = new ArrayList<>(roles.size());
        for (String role : roles) {
            authorities.add(new SimpleGrantedAuthority(Constants.ROLE_PREFIX + role));
        }
        return authorities;
    }

    @Override
    public String getPassword() {
        return user.getPassword();
    }

    @Override
    public String getUsername() {
        return user.getUsername();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    /**
     * 账号是否可用：0=禁用 1=正常
     */
    @Override
    public boolean isEnabled() {
        return user.getStatus() == null || user.getStatus() == Constants.USER_STATUS_NORMAL;
    }
}
