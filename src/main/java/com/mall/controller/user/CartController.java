package com.mall.controller.user;


import com.mall.common.Result;
import com.mall.service.ICartService;
import com.mall.vo.CartVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 购物车表 前端控制器
 * </p>
 *
 * @author 乐乐
 * @since 2026-08-16
 */
@Validated
@RestController("UserCartController")
@RequestMapping("/cart")
@Tag( name = "购物车管理",description = "购物车相关接口")
public class CartController {
    private final ICartService cartService;

    public CartController(ICartService cartService) {
        this.cartService = cartService;
    }
    @Operation(summary = "购物车列表",description = "购物车列表查询")
    @GetMapping()
    public Result<CartVO> listCarts(){
        CartVO cartVO = cartService.listCarts();
        return Result.success(cartVO);
    }
    @Operation(summary = "加入购物车")
    @PostMapping("/items")
    public Result addCart(@RequestParam Integer skuId,
                          @RequestParam @Min(value = 1, message = "数量必须大于 0") Integer quantity){
        cartService.addCart(skuId,quantity);
        return Result.success("已成功加入购物车");
    }
    @Operation(summary = "修改购物车商品数量")
    @PutMapping("/items/{id}")
    public Result  modifyQ(@RequestParam @Min(value = 1, message = "数量必须大于 0") Integer quantity
    ,@PathVariable Integer id) {
        cartService.modifyQ(quantity,id);
        return Result.success("数量修改成功");
    }
    @Operation(summary = "勾选/取消勾选")
    @PutMapping("/items/{id}/checked")
    public Result modifyC(@PathVariable Integer id,
                          @RequestParam Integer checked){
        cartService.modifyC(id,checked);
        return Result.success("操作成功");
    }
    @Operation(summary = "全选/取消全选")
    @PutMapping("/checked")
    public Result modifyAll(@RequestParam Integer checked){
        cartService.modifyAll(checked);
        return Result.success("操作成功");
    }
    @Operation(summary = "清空购物车")
    @DeleteMapping()
    public Result<?> deleteC(){
        cartService.deleteC(0);
        return Result.success("购物车已清空");
    }

    @Operation(summary = "删除已勾选的购物车项")
    @DeleteMapping("/checked")
    public Result<?> deleteChecked(){
        cartService.deleteC(1);
        return Result.success("已删除勾选的商品");
    }
    @Operation(summary = "删除购物车项")
    @DeleteMapping("/items/{id}")
    public Result deleteCartById(@PathVariable Integer id){
        cartService.deleteCartById(id);
        return Result.success("商品已从购物车移除");
    }
}
