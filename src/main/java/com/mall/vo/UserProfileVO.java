package com.mall.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户个人信息视图对象
 *
 * @author 乐乐
 */
@Data
public class UserProfileVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户 ID */
    private Long id;

    /** 用户名 */
    private String username;

    /** 昵称 */
    private String nickname;

    /** 头像 URL */
    private String avatar;

    /** 手机号 */
    private String phone;

    /** 邮箱 */
    private String email;

    /** 性别：0=未知 1=男 2=女 */
    private Integer gender;

    /** 账号状态：0=禁用 1=正常 */
    private Integer status;

    /** 最后登录时间 */
    private LocalDateTime lastLoginAt;

    /** 注册时间 */
    private LocalDateTime createdAt;

}
