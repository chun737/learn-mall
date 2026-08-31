package com.mall.dto;

import lombok.Data;

/**
 * 修改个人信息请求（字段均可选，传了才更新）
 *
 * @author 乐乐
 */
@Data
public class UpdateProfileDTO {

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
}
