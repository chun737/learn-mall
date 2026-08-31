package com.mall.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * <p>
 * 用户角色关联表（多对多）
 * </p>
 *
 * @author 乐乐
 * @since 2026-08-24
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("user_role")
public class UserRole implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 关联ID（主键）
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID（逻辑外键 -> user.id）
     */
    @TableField("user_id")
    private Long userId;

    /**
     * 角色ID（逻辑外键 -> role.id）
     */
    @TableField("role_id")
    private Long roleId;

    /**
     * 创建时间
     */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /**
     * 逻辑删除：0=未删除 1=已删除
     */
    @TableField("deleted")
    private Integer deleted;

}
