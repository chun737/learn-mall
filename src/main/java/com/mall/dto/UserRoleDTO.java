package com.mall.dto;

import lombok.Data;

/**
 * 给用户分配 / 移除角色的请求（管理员专用）
 *
 * @author 乐乐
 */
@Data
public class UserRoleDTO {

    /** 目标用户 ID */
    private Long userId;

    /** 角色编码（如 USER / ADMIN） */
    private String roleCode;
}
