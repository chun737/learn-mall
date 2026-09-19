package com.mall.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mall.common.Constants;
import com.mall.entity.Role;
import com.mall.entity.User;
import com.mall.entity.UserRole;
import com.mall.mapper.RoleMapper;
import com.mall.mapper.UserMapper;
import com.mall.mapper.UserRoleMapper;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 从数据库加载用户，供 Spring Security 登录认证使用
 *
 * @author 乐乐
 */
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;

    public UserDetailsServiceImpl(UserMapper userMapper,
                                  UserRoleMapper userRoleMapper,
                                  RoleMapper roleMapper) {
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                        .eq(User::getUsername, username)
                        .last("limit 1")
        );
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在: " + username);
        }
        // 联查用户角色，填充多角色列表（user_role -> role.role_code）
        user.setRoles(queryRolesByUserId(user.getId()));
        return new LoginUser(user);
    }

    /**
     * 查询某用户拥有的角色编码列表（多角色）
     */
    private List<String> queryRolesByUserId(Long userId) {
        List<UserRole> userRoles = userRoleMapper.selectList(
                new LambdaQueryWrapper<UserRole>()
                        .eq(UserRole::getUserId, userId)
        );
        if (userRoles == null || userRoles.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> roleIds = userRoles.stream()
                .map(UserRole::getRoleId)
                .collect(Collectors.toList());
        List<Role> roles = roleMapper.selectList(
                new LambdaQueryWrapper<Role>()
                        .in(Role::getId, roleIds)
        );
        return roles.stream()
                .map(Role::getRoleCode)
                .collect(Collectors.toList());
    }
}
