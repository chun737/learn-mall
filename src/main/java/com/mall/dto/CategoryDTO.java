package com.mall.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 商品分类新增/修改请求
 *
 * @author 乐乐
 */
@Data
public class CategoryDTO {

    /** 父分类 ID，缺省 0=顶级 */
    private Long parentId;

    /** 分类名称 */
    @NotBlank(message = "分类名称不能为空")
    private String name;

    /** 分类层级：1~3，缺省按父级自动计算 */
    private Integer level;

    /** 排序值（越小越靠前），默认 0 */
    private Integer sort;

    /** 分类图标 URL */
    private String icon;

    /** 状态：0=停用 1=启用，默认 1 */
    private Integer status;
}
