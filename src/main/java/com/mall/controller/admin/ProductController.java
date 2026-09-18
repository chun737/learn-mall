package com.mall.controller.admin;

import com.mall.common.Result;
import com.mall.dto.ProductDTO;
import com.mall.dto.SkuDTO;
import com.mall.service.IProductSearchService;
import com.mall.service.IProductService;
import com.mall.vo.AdminSkuVO;
import com.mall.vo.PageResult;
import com.mall.vo.ProductDetailVO;
import com.mall.vo.ProductListAdminVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController("AdminProductController")
@RequestMapping("/admin/product")
@Tag(name = "商品后台管理")
public class ProductController {
    private final IProductService productService;
    private final IProductSearchService productSearchService;

    public ProductController(IProductService productService, IProductSearchService productSearchService) {
        this.productService = productService;
        this.productSearchService = productSearchService;
    }

    @PostMapping("/es/import")
    @Operation(summary = "ES全量同步：把MySQL全部在售商品灌入product索引（幂等，重复调用会覆盖）")
    public Result<String> importAllToEs() {
        int count = productSearchService.importAll();
        return Result.success("ES 全量同步完成，共 " + count + " 条");
    }
    @GetMapping()
    @Operation(summary = "商品列表")
    public Result<PageResult<ProductListAdminVO>> ListProduct(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime){
        PageResult<ProductListAdminVO> vo = productService.listProdctsAdmin(pageNum,pageSize,categoryId,keyword,status,startTime,endTime);
        return Result.success(vo);
    }
    @PostMapping()
    @Operation(summary = "新增商品")
    public Result<ProductDetailVO> addProduct(@Valid @RequestBody ProductDTO productDTO){
        ProductDetailVO productDetailVO = productService.addProduct(productDTO);
        return Result.success(productDetailVO);
    }
    @PutMapping("/{id}")
    @Operation(summary = "修改商品")
    public Result<?> modifyProduct(@PathVariable("id") Long id,
                                   @RequestBody ProductDTO productDTO){
        productService.modifyProduct(id, productDTO);
        return Result.success("商品修改成功");
    }
    @PutMapping("/{id}/status")
    @Operation(summary = "商品上架/下架")
    public Result<String> modifyStatus(@PathVariable Long id,
                                       @RequestParam Integer status){
        String message = productService.modifyStatus(id,status);
        return Result.success(message);
    }
    @DeleteMapping("/{id}")
    @Operation(summary = "删除商品")
    public Result<?> deleteProduct(@PathVariable Long id){
        productService.deleteProduct(id);
        return Result.success("商品删除成功");
    }
    @GetMapping("/{id}/skus")
    @Operation(summary = "查询SKU列表")
    public Result<List<AdminSkuVO>> getSkus(@PathVariable Long id){
        List<AdminSkuVO> adminSkuVOList = productService.getSKUs(id);
        return Result.success(adminSkuVOList);
    }
    @Operation(summary = "新增skus")
    @PostMapping("/{id}/skus")
    public Result<?> addSkus(@PathVariable Long id,
                             @Valid @RequestBody SkuDTO skuDTO){
        productService.addSkus(id,skuDTO);
        return Result.success("商品添加成功");
    }
}
