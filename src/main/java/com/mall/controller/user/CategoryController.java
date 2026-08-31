package com.mall.controller.user;


import com.mall.common.Result;
import com.mall.service.ICategoryService;
import com.mall.vo.CategoryVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * <p>
 * 商品分类表（多级） 前端控制器
 * </p>
 *
 * @author 乐乐
 * @since 2026-08-16
 */
@RestController("UserCategoryController")
@RequestMapping("/categories")
@Tag( name = "分类管理",description = "后台商品分类管理")
public class CategoryController {

    private final ICategoryService categoryService;

    public CategoryController(ICategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping()
    @Operation(summary = "分类商品树")
    public Result<List<CategoryVO>> categoryTree() {
        return Result.success(categoryService.categoryTree());
    }

}
