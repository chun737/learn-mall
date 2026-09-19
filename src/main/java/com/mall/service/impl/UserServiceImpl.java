package com.mall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.github.pagehelper.PageInfo;
import com.mall.common.BusinessException;
import com.mall.common.PageUtils;
import com.mall.common.Constants;
import com.mall.dto.AddressDTO;
import com.mall.dto.AdminUserDTO;
import com.mall.dto.ForgetPasswordDTO;
import com.mall.dto.UpdateProfileDTO;
import com.mall.dto.UserRoleDTO;
import com.mall.entity.Role;
import com.mall.entity.User;
import com.mall.entity.UserAddress;
import com.mall.entity.UserRole;
import com.mall.mapper.RoleMapper;
import com.mall.mapper.UserAddressMapper;
import com.mall.mapper.UserMapper;
import com.mall.mapper.UserRoleMapper;
import com.mall.service.IUserService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mall.util.SecurityUtils;
import com.mall.vo.AddressVO;
import com.mall.vo.AdminUserVO;
import com.mall.vo.PageResult;
import com.mall.vo.UserProfileVO;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.mall.enums.ErrorCode.NOT_FOUND;
import static com.mall.enums.ErrorCode.PASSWORD_ERROR;
import static com.mall.enums.ErrorCode.ROLE_NOT_FOUND;
import static com.mall.enums.ErrorCode.USER_INFO_MISMATCH;
import static com.mall.enums.ErrorCode.USER_NOT_FOUND;
import static com.mall.enums.ErrorCode.USERNAME_EXIST;

/**
 * <p>
 * 用户账号表 服务实现类
 * </p>
 *
 * @author 乐乐
 * @since 2026-08-16
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate stringRedisTemplate;
    private final UserAddressMapper addressMapper;
    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;
    public UserServiceImpl(UserMapper userMapper, PasswordEncoder passwordEncoder,
                           StringRedisTemplate stringRedisTemplate
                            ,UserAddressMapper addressMapper,
                           UserRoleMapper userRoleMapper,
                           RoleMapper roleMapper) {
        this.passwordEncoder = passwordEncoder;
        this.userMapper = userMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.addressMapper = addressMapper;
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
    }

    @Override
    public UserProfileVO getProfile() {
        Long userId = SecurityUtils.getUserId();
        User user = userMapper.selectById(userId);
        if(user == null){
            throw new BusinessException(USER_NOT_FOUND);
        }
        UserProfileVO userProfileVO = new UserProfileVO();
        BeanUtils.copyProperties(user,userProfileVO);
        return  userProfileVO;
    }

    @Override
    public UserProfileVO modifyProfile(UpdateProfileDTO dto) {
        Long userId = SecurityUtils.getUserId();
        User user = userMapper.selectById(userId);
        if(user == null){
            throw new BusinessException(USER_NOT_FOUND);
        }
        // 白名单更新（只改允许的字段）
        user.setNickname(dto.getNickname());
        user.setAvatar(dto.getAvatar());
        user.setPhone(dto.getPhone());
        user.setEmail(dto.getEmail());
        user.setGender(dto.getGender());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);   // 只更新一次

        UserProfileVO userProfileVO1 = new UserProfileVO();
        BeanUtils.copyProperties(user, userProfileVO1);
        return userProfileVO1;
    }

    @Override
    public void modifyPd(String oldPassword, String newPassword) {
        Long userId = SecurityUtils.getUserId();
        User user = userMapper.selectById(userId);
        if(user == null){
            throw new BusinessException(USER_NOT_FOUND);
        }
        if(!passwordEncoder.matches(oldPassword, user.getPassword())){
            throw new BusinessException(PASSWORD_ERROR);
        }
        else {
            user.setPassword(passwordEncoder.encode(newPassword));
            user.setUpdatedAt(LocalDateTime.now());
            userMapper.updateById(user);
            // 删除 Redis 中的 token 白名单，强制用户重新登录
            stringRedisTemplate.delete(Constants.LOGIN_TOKEN_PREFIX + userId);
        }
    }

    @Override
    public void forgetPassword(ForgetPasswordDTO dto) {
        // 1. 按用户名查用户
        User user = userMapper.selectOne(
                new QueryWrapper<User>().eq("username", dto.getUsername()));
        if (user == null) {
            throw new BusinessException(USER_NOT_FOUND);
        }

        // 2. 校验手机号是否匹配（身份验证，防止盗用他人账号重置密码）
        if (user.getPhone() == null || !user.getPhone().equals(dto.getPhone())) {
            throw new BusinessException(USER_INFO_MISMATCH);
        }

        // 3. 重置密码（BCrypt 加密）
        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);

        // 4. 删除 Redis token 白名单，强制用户重新登录
        stringRedisTemplate.delete(Constants.LOGIN_TOKEN_PREFIX + user.getId());
    }

    @Override
    public AddressVO addAddress(AddressDTO addressDTO) {
        Long userId = SecurityUtils.getUserId();
        UserAddress address = new UserAddress();
        BeanUtils.copyProperties(addressDTO,address);
        address.setUserId(userId);
        address.setCreatedAt(LocalDateTime.now());
        AddressVO addressVO = new AddressVO();
        addressMapper.insert(address);
        BeanUtils.copyProperties(address,addressVO);
        return addressVO;
    }

    @Override
    public void deleteAddress(Long id) {
        Long userId = SecurityUtils.getUserId();

        // 越权校验：地址必须存在且属于当前用户
        UserAddress target = addressMapper.selectById(id);
        if (target == null || !target.getUserId().equals(userId)) {
            throw new BusinessException(NOT_FOUND);
        }

        // 逻辑删除：@TableLogic 下 delete(wrapper) 自动改写为
        // UPDATE user_address SET deleted=1 WHERE id=? AND user_id=? AND deleted=0（越权防护条件保留）
        // ⭐ 不能再用 update(entity)+setDeleted：MP 会把逻辑删除字段从 SET 子句排除，只剩 updated_at 生效（静默失效）
        QueryWrapper<UserAddress> wrapper = new QueryWrapper<UserAddress>()
                .eq("id", id)
                .eq("user_id", userId);
        addressMapper.delete(wrapper);
    }

    @Override
    public void modifyAddress(AddressDTO addressDTO, Long id) {
        Long userId = SecurityUtils.getUserId();

        // 越权校验：地址必须存在且属于当前用户
        UserAddress target = addressMapper.selectById(id);
        if (target == null || !target.getUserId().equals(userId)) {
            throw new BusinessException(NOT_FOUND);
        }

        // 只更新允许修改的字段
        UserAddress address = new UserAddress();
        BeanUtils.copyProperties(addressDTO, address);
        address.setUpdatedAt(LocalDateTime.now());
        QueryWrapper<UserAddress> wrapper = new QueryWrapper<UserAddress>()
                .eq("id", id)
                .eq("user_id", userId);
        addressMapper.update(address, wrapper);
    }

    @Override
    public ArrayList<AddressVO> listAddresses() {
        Long userId = SecurityUtils.getUserId();
        // 查询出该用户所有未删除地址（XML 中已按默认地址降序排序）
        ArrayList<UserAddress> addressList = addressMapper.listAddresses(userId);

        // 队列泛型转换：UserAddress -> AddressVO
        return addressList.stream()
                .map(address -> {
                    AddressVO vo = new AddressVO();
                    BeanUtils.copyProperties(address, vo);
                    return vo;
                })
                .collect(Collectors.toCollection(ArrayList::new));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void isdefaultById(Long id) {
        Long userId = SecurityUtils.getUserId();

        // 1. 校验目标地址存在且属于当前用户（防越权改别人的地址）
        UserAddress target = addressMapper.selectById(id);
        if (target == null || !target.getUserId().equals(userId)) {
            throw new BusinessException(NOT_FOUND);
        }
        // 2. 先清空该用户所有地址的默认标记（条件批量更新，无需先查出全部）
        QueryWrapper<UserAddress> clearWrapper = new QueryWrapper<UserAddress>()
                .eq("user_id", userId)
                .eq("deleted", 0);
        UserAddress clear = new UserAddress();
        clear.setIsDefault(Constants.NOT_DEFAULT);
        addressMapper.update(clear, clearWrapper);

        // 3. 再把目标地址置为默认
        QueryWrapper<UserAddress> setWrapper = new QueryWrapper<UserAddress>()
                .eq("id", id)
                .eq("user_id", userId);
        UserAddress setDefault = new UserAddress();
        setDefault.setIsDefault(Constants.IS_DEFAULT);
        addressMapper.update(setDefault, setWrapper);
    }

    // ==================== 后台用户管理 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminUserVO createUser(AdminUserDTO dto) {
        // 1. 校验用户名唯一
        Long existCount = userMapper.selectCount(
                new QueryWrapper<User>().eq("username", dto.getUsername()));
        if (existCount != null && existCount > 0) {
            throw new BusinessException(USERNAME_EXIST);
        }

        // 2. 组装用户对象，密码 BCrypt 加密
        User user = new User();
        BeanUtils.copyProperties(dto, user);
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setStatus(dto.getStatus() == null ? Constants.USER_STATUS_NORMAL : dto.getStatus());
        user.setGender(dto.getGender() == null ? Constants.GENDER_UNKNOWN : dto.getGender());
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        user.setDeleted(Constants.NOT_DELETED);
        userMapper.insert(user);

        // 3. 绑定角色（默认 USER，若传了 roleCodes 则按传入的绑定）
        List<String> roleCodes = dto.getRoleCodes();
        if (roleCodes == null || roleCodes.isEmpty()) {
            roleCodes = Collections.singletonList(Constants.ROLE_USER);
        }
        for (String roleCode : roleCodes) {
            bindRole(user.getId(), roleCode);
        }

        // 4. 返回用户信息（含角色）
        return buildAdminUserVO(user, roleCodes);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignRole(UserRoleDTO dto) {
        // 校验用户存在
        User user = userMapper.selectById(dto.getUserId());
        if (user == null) {
            throw new BusinessException(USER_NOT_FOUND);
        }
        bindRole(dto.getUserId(), dto.getRoleCode());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeRole(UserRoleDTO dto) {
        // 1. 校验用户存在
        User user = userMapper.selectById(dto.getUserId());
        if (user == null) {
            throw new BusinessException(USER_NOT_FOUND);
        }
        // 2. 查询角色
        Role role = getRoleByCode(dto.getRoleCode());
        if (role == null) {
            throw new BusinessException(ROLE_NOT_FOUND);
        }
        // 3. 逻辑删除该用户与角色的关联（@TableLogic 下 delete(wrapper) 自动改写为 UPDATE deleted=1，
        //    并自动追加 deleted=0 条件；原 setDeleted+update 写法会把 deleted 排除出 SET 子句而静默失效）
        userRoleMapper.delete(new LambdaQueryWrapper<UserRole>()
                .eq(UserRole::getUserId, dto.getUserId())
                .eq(UserRole::getRoleId, role.getId()));
    }

    @Override
    public PageResult<AdminUserVO> listUsers(Integer pageNum, Integer pageSize, String keyword) {
        // 1. 分页查询用户（按用户名/昵称模糊）
        PageUtils.startPage(pageNum, pageSize);
        QueryWrapper<User> wrapper = new QueryWrapper<User>()
                .eq("deleted", Constants.NOT_DELETED);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like("username", keyword).or().like("nickname", keyword));
        }
        wrapper.orderByDesc("id");
        List<User> users = userMapper.selectList(wrapper);
        PageInfo<User> pageInfo = new PageInfo<>(users);

        if (users.isEmpty()) {
            return PageResult.empty(pageNum, pageSize);
        }

        // 2. 批量查询这些用户的角色（避免 N+1）
        List<Long> userIds = users.stream().map(User::getId).collect(Collectors.toList());
        Map<Long, List<String>> roleMap = queryRolesMapByUserIds(userIds);

        // 3. 组装 VO
        List<AdminUserVO> voList = users.stream().map(user -> {
            AdminUserVO vo = new AdminUserVO();
            BeanUtils.copyProperties(user, vo);
            List<String> roles = roleMap.getOrDefault(user.getId(), Collections.emptyList());
            vo.setRoles(roles);
            return vo;
        }).collect(Collectors.toList());

        return PageResult.of(pageInfo, voList);
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 绑定用户与角色（user_role 关联表），已存在则跳过（幂等）
     */
    private void bindRole(Long userId, String roleCode) {
        Role role = getRoleByCode(roleCode);
        if (role == null) {
            throw new BusinessException(ROLE_NOT_FOUND);
        }
        Long existCount = userRoleMapper.selectCount(
                new LambdaQueryWrapper<UserRole>()
                        .eq(UserRole::getUserId, userId)
                        .eq(UserRole::getRoleId, role.getId())
                        .eq(UserRole::getDeleted, Constants.NOT_DELETED));
        if (existCount != null && existCount > 0) {
            return; // 已存在，幂等跳过
        }
        UserRole userRole = new UserRole();
        userRole.setUserId(userId);
        userRole.setRoleId(role.getId());
        userRole.setCreatedAt(LocalDateTime.now());
        userRole.setDeleted(Constants.NOT_DELETED);
        userRoleMapper.insert(userRole);
    }

    /**
     * 根据角色编码查询角色（未删除）
     */
    private Role getRoleByCode(String roleCode) {
        return roleMapper.selectOne(
                new LambdaQueryWrapper<Role>()
                        .eq(Role::getRoleCode, roleCode)
                        .eq(Role::getDeleted, Constants.NOT_DELETED)
                        .last("limit 1"));
    }

    /**
     * 批量查询多个用户的角色编码，返回 userId -> 角色编码列表 的映射
     */
    private Map<Long, List<String>> queryRolesMapByUserIds(List<Long> userIds) {
        // 查这些用户的所有关联
        List<UserRole> userRoles = userRoleMapper.selectList(
                new LambdaQueryWrapper<UserRole>()
                        .in(UserRole::getUserId, userIds)
                        .eq(UserRole::getDeleted, Constants.NOT_DELETED));
        if (userRoles.isEmpty()) {
            return Collections.emptyMap();
        }
        // 查角色字典
        List<Long> roleIds = userRoles.stream()
                .map(UserRole::getRoleId)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, String> roleCodeMap = roleMapper.selectList(
                        new LambdaQueryWrapper<Role>()
                                .in(Role::getId, roleIds)
                                .eq(Role::getDeleted, Constants.NOT_DELETED))
                .stream()
                .collect(Collectors.toMap(Role::getId, Role::getRoleCode));

        // 组装 userId -> roles
        return userRoles.stream()
                .collect(Collectors.groupingBy(
                        UserRole::getUserId,
                        Collectors.mapping(ur -> roleCodeMap.get(ur.getRoleId()), Collectors.toList())));
    }

    /**
     * 构建后台用户 VO（含角色）
     */
    private AdminUserVO buildAdminUserVO(User user, List<String> roles) {
        AdminUserVO vo = new AdminUserVO();
        BeanUtils.copyProperties(user, vo);
        vo.setRoles(roles == null ? Collections.emptyList() : roles);
        return vo;
    }


}
