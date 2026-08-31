package com.mall.service;

import com.mall.dto.CategoryDTO;
import com.mall.entity.Category;
import com.baomidou.mybatisplus.extension.service.IService;
import com.mall.vo.CategoryVO;

import java.util.List;

/**
 * <p>
 * 商品分类表（多级） 服务类
 * </p>
 *
 * @author 乐乐
 * @since 2026-08-16
 */
public interface ICategoryService extends IService<Category> {

    /**
     * 查询分类树（前台：仅启用且未删除的分类）
     */
    List<CategoryVO> categoryTree();

    void addCategory(CategoryDTO categoryDTO);

    List<CategoryVO> listAllCategoryTree();

    void modifyCategory(Long id, CategoryDTO categoryDTO);

    /**
     * 删除分类（逻辑删除）：存在子分类或关联商品时禁止删除
     */
    void deleteCategory(Long id);
}
