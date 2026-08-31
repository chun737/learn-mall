package com.mall.service;

import com.mall.dto.AddressDTO;
import com.mall.dto.AdminUserDTO;
import com.mall.dto.ForgetPasswordDTO;
import com.mall.dto.UpdateProfileDTO;
import com.mall.dto.UserRoleDTO;
import com.mall.entity.User;
import com.baomidou.mybatisplus.extension.service.IService;
import com.mall.vo.AddressVO;
import com.mall.vo.AdminUserVO;
import com.mall.vo.PageResult;
import com.mall.vo.UserProfileVO;

import java.util.ArrayList;

/**
 * <p>
 * 用户账号表 服务类
 * </p>
 *
 * @author 乐乐
 * @since 2026-08-16
 */
public interface IUserService extends IService<User> {


    UserProfileVO getProfile();

    UserProfileVO modifyProfile(UpdateProfileDTO userProfileVO);

    void modifyPd(String oldPassword, String newPassword);

    /**
     * 忘记密码：校验用户名 + 手机号后重置密码
     */
    void forgetPassword(ForgetPasswordDTO dto);

    AddressVO addAddress(AddressDTO addressDTO);

    void deleteAddress(Long id);


    void modifyAddress(AddressDTO addressDTO, Long id);

    ArrayList<AddressVO> listAddresses();

    void isdefaultById(Long id);

    // ==================== 后台用户管理 ====================

    /**
     * 后台创建用户（管理员），可同时绑定多个角色
     */
    AdminUserVO createUser(AdminUserDTO dto);

    /**
     * 给用户分配一个角色（多角色）
     */
    void assignRole(UserRoleDTO dto);

    /**
     * 移除用户的一个角色（多角色）
     */
    void removeRole(UserRoleDTO dto);

    /**
     * 后台分页查询用户列表（含角色列表）
     */
    PageResult<AdminUserVO> listUsers(Integer pageNum, Integer pageSize, String keyword);
}
