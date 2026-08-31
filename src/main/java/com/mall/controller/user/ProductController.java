package com.mall.controller.user;


import com.mall.common.Result;
import com.mall.service.IProductService;
import com.mall.vo.PageResult;
import com.mall.vo.ProductDetailVO;
import com.mall.vo.ProductListVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * <p>
 * 商品表（SPU） 前端控制器
 * </p>
 *
 * @author 乐乐
 * @since 2026-08-16
 */
@RestController("UserProductController")
@RequestMapping("/product")
@Tag(name = "商品管理",description = "后台商品管理")
public class ProductController {
    private IProductService productService;

    public ProductController(IProductService productService) {
        this.productService = productService;
    }

    @GetMapping()
    @Operation(summary = "分页商品列表查询")
    public Result<PageResult<ProductListVO>> listProducts(
             @RequestParam (defaultValue = "1") Integer pageNum,
             @RequestParam(defaultValue = "10") Integer pageSize,
             @RequestParam(required = false) Long categoryId,
             @RequestParam(required = false) String keyword,
             @RequestParam(defaultValue = "default") String sortBy){
        PageResult<ProductListVO> result = productService.listProducts(pageNum,pageSize,categoryId,keyword,sortBy);
        return Result.success(result);
    }
    @GetMapping("/{id}")
    @Operation(summary = "商品详情查询")
    public Result<ProductDetailVO> getDPs(@PathVariable Long id){
        ProductDetailVO vos = productService.getDPs(id);
        return Result.success(vos);
    }
    @GetMapping("/suggest")
    @Operation(summary = "热门商品推荐")
    public Result<List<String>> getHot10(@RequestParam(required = false) String keyword){
        List<String> hot10 = productService.getHot10(keyword);
        return Result.success(hot10);
    }
}
