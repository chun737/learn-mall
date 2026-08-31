package com.mall.controller.admin;

import com.mall.common.Result;
import com.mall.dto.CategoryDTO;
import com.mall.service.ICategoryService;
import com.mall.vo.CategoryVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController("AdminCategoryController")
@RequestMapping("/admin/category")
@Tag(name = "后台分类管理")
public class CategoryController {
    private final ICategoryService categoryService;

    public CategoryController(ICategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @PostMapping()
    @Operation(summary = "新增分类")
    public Result<?> addCategory(@Valid @RequestBody CategoryDTO categoryDTO) {
        categoryService.addCategory(categoryDTO);
        return Result.success("分类创建成功");
    }

    @GetMapping
    @Operation(summary = "分类列表")
    public Result<List<CategoryVO>> listCategory() {
        return Result.success(categoryService.listAllCategoryTree());
    }

    @PutMapping("/{id}")
    @Operation(summary = "修改分类")
    public Result<?> modifyCategory(@PathVariable("id") Long id,
                                    @Valid @RequestBody CategoryDTO categoryDTO) {
        categoryService.modifyCategory(id, categoryDTO);
        return Result.success("分类修改成功");
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除分类")
    public Result<?> deleteCategory(@PathVariable("id") Long id) {
        categoryService.deleteCategory(id);
        return Result.success("分类删除成功");
    }
}
