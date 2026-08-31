package com.mall.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import java.time.LocalDateTime;
import java.io.Serializable;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * <p>
 * 用户账号表
 * </p>
 *
 * @author 乐乐
 * @since 2026-08-16
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("user")
public class User implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 用户ID（主键）
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 登录用户名（唯一）
     */
    @TableField("username")
    private String username;

    /**
     * 登录密码（加密存储，如 BCrypt，绝不存明文）
     */
    @TableField("password")
    private String password;

    /**
     * 昵称
     */
    @TableField("nickname")
    private String nickname;

    /**
     * 头像 URL
     */
    @TableField("avatar")
    private String avatar;

    /**
     * 手机号
     */
    @TableField("phone")
    private String phone;

    /**
     * 邮箱
     */
    @TableField("email")
    private String email;

    /**
     * 性别：0=未知 1=男 2=女
     */
    @TableField("gender")
    private Integer gender;

    /**
     * 账号状态：0=禁用 1=正常
     */
    @TableField("status")
    private Integer status;

    /**
     * 角色编码列表（多角色，非数据库字段，由 user_role + role 联查后填充）
     * 如 ["USER", "ADMIN"]。Lombok @Data 会生成 getRoles() / setRoles()
     */
    @TableField(exist = false)
    private List<String> roles;

    /**
     * 最后登录时间
     */
    @TableField("last_login_at")
    private LocalDateTime lastLoginAt;

    /**
     * 创建时间
     */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @TableField("updated_at")
    private LocalDateTime updatedAt;

    /**
     * 逻辑删除：0=未删除 1=已删除
     */
    @TableField("deleted")
    private Integer deleted;


}
