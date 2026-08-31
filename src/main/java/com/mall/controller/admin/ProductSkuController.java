package com.mall.controller.admin;

import com.mall.common.Result;
import com.mall.dto.SkuDTO;
import com.mall.dto.SkuStatusDTO;
import com.mall.dto.StockAdjustDTO;
import com.mall.service.IProductSkuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController("AdminProductSkuController")
@RequestMapping("/admin/sku")
@Tag(name = "SKU管理",description = "调整SKU")
public class ProductSkuController {
    private final IProductSkuService productSkuService;

    public ProductSkuController(IProductSkuService productSkuService) {
        this.productSkuService = productSkuService;
    }

    @PutMapping("/{id}")
    @Operation(summary = "修改Sku")
    public Result<?> modifySkus(@Valid @RequestBody SkuDTO skuDTO,
                                @PathVariable("id") Long id){
        productSkuService.modifySku(id, skuDTO);
        return Result.success("SKU修改成功");
    }
    @PutMapping("/{id}/stock")
    @Operation(summary = "调整库存")
    public Result<Integer> modifySkuQty(@PathVariable("id") Long id,
                                        @RequestBody StockAdjustDTO dto){
        Integer newStock = productSkuService.adjustStock(id, dto.getChangeQty(), dto.getRemark());
        return Result.success(newStock);
    }
    @PutMapping("/{id}/status")
    @Operation(summary = "SKU 上架/下架")
    public Result<?> modifySkuStatus(@PathVariable("id") Long id,
                                     @RequestBody SkuStatusDTO dto){
        productSkuService.modifySkuStatus(id, dto.getStatus());
        return Result.success(dto.getStatus() == 1 ? "SKU上架成功" : "SKU下架成功");
    }
}
