package com.mall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mall.common.BusinessException;
import com.mall.common.Constants;
import com.mall.dto.LoginRequest;
import com.mall.dto.LoginResponse;
import com.mall.dto.RegisterRequest;
import com.mall.dto.RegisterResponse;
import com.mall.entity.Role;
import com.mall.entity.User;
import com.mall.entity.UserRole;
import com.mall.mapper.RoleMapper;
import com.mall.mapper.UserMapper;
import com.mall.mapper.UserRoleMapper;
import com.mall.service.AuthService;
import com.mall.util.JwtUtil;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.mall.enums.ErrorCode.*;

/**
 * 认证服务实现：注册、登录
 *
 * @author 乐乐
 */
@Service
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;

    public AuthServiceImpl(UserMapper userMapper,
                           UserRoleMapper userRoleMapper,
                           RoleMapper roleMapper,
                           StringRedisTemplate stringRedisTemplate,
                           PasswordEncoder passwordEncoder,
                           AuthenticationManager authenticationManager,
                           JwtUtil jwtUtil) {
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RegisterResponse register(RegisterRequest request) {
        String username = request.getUsername();

        // 1. 校验用户名唯一
        QueryWrapper<User> wrapper = new QueryWrapper<User>().eq("username", username);
        User exist = userMapper.selectOne(wrapper);
        if (exist != null) {
            throw new BusinessException(USERNAME_EXIST);
        }

        // 2. 组装用户对象，密码 BCrypt 加密（绝不存明文）
        User user = new User();
        BeanUtils.copyProperties(request, user);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setStatus(Constants.USER_STATUS_NORMAL);
        user.setGender(request.getGender() == null ? Constants.GENDER_UNKNOWN : request.getGender());
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        user.setDeleted(Constants.NOT_DELETED);

        // 3. 落库（插入后 user.getId() 会被自增主键回填）
        userMapper.insert(user);

        // 4. 绑定默认角色 USER（user_role 关联表），注册默认普通用户
        Long userRoleId = getRoleIdByCode(Constants.ROLE_USER);
        UserRole userRole = new UserRole();
        userRole.setUserId(user.getId());
        userRole.setRoleId(userRoleId);
        userRole.setCreatedAt(LocalDateTime.now());
        userRole.setDeleted(Constants.NOT_DELETED);
        userRoleMapper.insert(userRole);

        // 5. 注册即登录：直接签发 token（角色列表），无需再次登录
        List<String> roles = Collections.singletonList(Constants.ROLE_USER);
        String token = jwtUtil.createToken(user.getId(), user.getUsername(), roles);

        // 6. 写入 Redis 白名单（与登录一致，供过滤器比对）
        stringRedisTemplate.opsForValue()
                .set(Constants.LOGIN_TOKEN_PREFIX + user.getId(), token, jwtUtil.getExpireTime(), TimeUnit.MILLISECONDS);

        // 7. 返回 token + 用户信息
        return new RegisterResponse(user.getId(), user.getUsername(), user.getNickname(), token);
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        return doLogin(request, false);
    }

    @Override
    public LoginResponse adminLogin(LoginRequest request) {
        return doLogin(request, true);
    }

    /**
     * 登录核心逻辑：锁定检查 → 认证 → 签发 token → 写白名单。
     *
     * @param requireAdmin true 时额外要求用户具备 ADMIN 角色（后台登录入口专用），
     *                     否则拒绝，避免普通用户从 /admin/auth/login 拿到 token
     */
    private LoginResponse doLogin(LoginRequest request, boolean requireAdmin) {
        String failKey = Constants.LOGIN_FAIL_PREFIX + request.getUsername();

        // 0. 锁定前置检查：当日失败已达 5 次的账号直接拒绝，不再尝试认证（爆破成本被压到每天 5 次真实尝试）
        String failStr = stringRedisTemplate.opsForValue().get(failKey);
        if (failStr != null && Integer.parseInt(failStr) >= Constants.MAX_LOGIN_ATTEMPTS) {
            throw new BusinessException(USER_LOCK);
        }

        // 1. 交给 Security 的 AuthenticationManager 认证（内部调 UserDetailsService + BCrypt 校验）。
        //    异常分类：DisabledException = 账号被禁用（LoginUser.isEnabled=false，由 DB status=0 触发，
        //    且发生在密码校验之前，密码对错无关）；其余 AuthenticationException = 密码错误或用户不存在
        //    （Spring 默认把后者伪装成前者，顺带消除用户名枚举），统一计数并报剩余次数。
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));
        } catch (DisabledException e) {
            throw new BusinessException(ACCOUNT_DISABLED);
        } catch (AuthenticationException e) {
            Long count = stringRedisTemplate.opsForValue().increment(failKey);
            if (count != null && count == 1) {
                // 首次失败才设置过期：到今晚零点（"当天"的语义）；并发失败不会刷掉过期时间
                LocalDateTime midnight = LocalDateTime.now().toLocalDate().plusDays(1).atStartOfDay();
                stringRedisTemplate.expire(failKey, Duration.between(LocalDateTime.now(), midnight));
            }
            long used = count == null ? 1 : count;
            int remaining = (int) Math.max(0, Constants.MAX_LOGIN_ATTEMPTS - used);
            if (remaining <= 0) {
                throw new BusinessException(USER_LOCK);            // 第 5 次失败：锁定
            }
            throw new BusinessException(PASSWORD_ERROR.getCode(),
                    "用户名或密码错误，剩余 " + remaining + " 次机会");   // 剩余次数经 message 到达前端
        }

        // 2. 认证通过：清空失败计数（成功即重置），查询用户
        stringRedisTemplate.delete(failKey);
        User user = userMapper.selectOne(
                new QueryWrapper<User>().eq("username", request.getUsername()).last("limit 1"));
        if (user == null) {
            throw new BusinessException(USER_NOT_FOUND);
        }

        // 3. 签发 JWT：从数据库动态获取用户的角色列表（多角色）
        List<String> roles = queryRolesByUserId(user.getId());
        if (roles == null || roles.isEmpty()) {
            roles = Collections.singletonList(Constants.ROLE_USER);
        }
        // 后台登录入口要求 ADMIN 角色（此时尚未签发 token、尚未写白名单，抛异常干净无副作用）
        if (requireAdmin && !roles.contains(Constants.ROLE_ADMIN)) {
            throw new BusinessException(USER_NOT_FOUND);
        }
        String token = jwtUtil.createToken(user.getId(), user.getUsername(), roles);

        // 4. 写入 Redis 白名单（登录态，供过滤器比对、支持主动失效）
        stringRedisTemplate.opsForValue()
                .set(Constants.LOGIN_TOKEN_PREFIX + user.getId(), token, jwtUtil.getExpireTime(), TimeUnit.MILLISECONDS);

        // 5. 更新最后登录时间
        user.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(user);

        // 6. 返回 token + 用户信息
        return new LoginResponse(token, user.getId(), user.getUsername());
    }
    /**
     * 根据角色编码查询角色 ID
     */
    private Long getRoleIdByCode(String roleCode) {
        Role role = roleMapper.selectOne(
                new LambdaQueryWrapper<Role>()
                        .eq(Role::getRoleCode, roleCode)
                        .last("limit 1")
        );
        if (role == null) {
            throw new BusinessException(USER_NOT_FOUND);
        }
        return role.getId();
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
            return Collections.emptyList();
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
