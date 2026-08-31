package com.mall.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 用户注册/登录响应视图对象
 *
 * @author 乐乐
 */
@Data
public class AuthVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户 ID */
    private Long id;

    /** 用户名 */
    private String username;

    /** 昵称 */
    private String nickname;

    /** 头像 URL，未设置为 null */
    private String avatar;

    /** 登录凭证 JWT */
    private String token;

}
