package com.mall.dto;

import lombok.Data;

import java.util.List;

/**
 * 后台创建用户请求（管理员专用）
 *
 * @author 乐乐
 */
@Data
public class AdminUserDTO {

    /** 登录用户名（唯一） */
    private String username;

    /** 登录密码 */
    private String password;

    /** 昵称 */
    private String nickname;

    /** 手机号 */
    private String phone;

    /** 邮箱 */
    private String email;

    /** 性别：0=未知 1=男 2=女 */
    private Integer gender;

    /** 账号状态：0=禁用 1=正常 */
    private Integer status;

    /** 角色编码列表（可为空，默认为 USER），如 ["USER","ADMIN"] */
    private List<String> roleCodes;
}
