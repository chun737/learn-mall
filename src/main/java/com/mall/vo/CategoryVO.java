package com.mall.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 商品分类（树形）视图对象
 *
 * @author 乐乐
 */
@Data
public class CategoryVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 分类 ID */
    private Long id;

    /** 父分类 ID，顶级为 0 */
    private Long parentId;

    /** 分类名称 */
    private String name;

    /** 层级 1~3 */
    private Integer level;

    /** 分类图标 URL */
    private String icon;

    /** 子分类列表 */
    private List<CategoryVO> children;

}
