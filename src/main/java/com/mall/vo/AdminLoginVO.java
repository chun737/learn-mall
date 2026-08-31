package com.mall.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 管理员登录响应视图对象
 *
 * @author 乐乐
 */
@Data
public class AdminLoginVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 管理员 ID */
    private Long adminId;

    /** 管理员用户名 */
    private String username;

    /** 角色标识 */
    private String role;

    /** 登录凭证 JWT */
    private String token;

}
