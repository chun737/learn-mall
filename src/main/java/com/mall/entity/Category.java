package com.mall.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import java.time.LocalDateTime;
import java.io.Serializable;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * <p>
 * 商品分类表（多级）
 * </p>
 *
 * @author 乐乐
 * @since 2026-08-16
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("category")
public class Category implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 分类ID（主键）
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 父分类ID，0=顶级分类（逻辑外键 -> category.id）
     */
    @TableField("parent_id")
    private Long parentId;

    /**
     * 分类名称
     */
    @TableField("name")
    private String name;

    /**
     * 分类层级：1=一级 2=二级 3=三级
     */
    @TableField("level")
    private Integer level;

    /**
     * 排序值（越小越靠前）
     */
    @TableField("sort")
    private Integer sort;

    /**
     * 分类图标 URL
     */
    @TableField("icon")
    private String icon;

    /**
     * 状态：0=停用 1=启用
     */
    @TableField("status")
    private Integer status;

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
