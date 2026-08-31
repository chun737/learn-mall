package com.mall.controller.user;


import com.mall.common.Result;
import com.mall.dto.OrderCreateDTO;
import com.mall.service.IOrderService;
import com.mall.vo.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 订单主表 前端控制器
 * </p>
 *
 * @author 乐乐
 * @since 2026-08-16
 */
@Validated
@RestController("UserOrderController")
@RequestMapping("/order")
@Tag(name = "订单管理",description = "订单后台管理")
public class OrderController {
    private final IOrderService orderService;

    public OrderController(IOrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/preview")
    @Operation(summary = "下单预览",description = "查询订单列表")
    public Result<OrderPreviewVO> getOrderPreview(){
        OrderPreviewVO orderPreviewVO =  orderService.getOrderPreview();
        return Result.success(orderPreviewVO);
    }
    @PostMapping()
    @Operation(summary = "创建订单")
    public Result<OrderCreateVO> addOrder(@Valid @RequestBody OrderCreateDTO orderCreateDTO){
         OrderCreateVO vo =  orderService.addOrder(orderCreateDTO);
         return Result.success(vo);
    }
    @GetMapping()
    @Operation(summary = "订单列表")
    public Result<PageResult<OrderListVO>> getOrders(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Integer orderStatus){
        PageResult<OrderListVO> orderList = orderService.getOrders(pageNum, pageSize, orderStatus);
        return Result.success(orderList);
    }
    @GetMapping("/{orderNo}")
    @Operation(summary = "订单详情")
    public Result<OrderDetailVO> getOrderDetail(@PathVariable String orderNo){
            OrderDetailVO orderDetailVO = orderService.getOrderDetail(orderNo);
            return Result.success(orderDetailVO);
    }
    @PutMapping("/{orderNo}/cancel")
    @Operation(summary = "取消订单")
    public Result cancelOrder(@PathVariable String orderNo){
        orderService.cancelOrder(orderNo);
        return Result.success("订单取消成功");
    }
    @PutMapping("/{orderNo}")
    @Operation(summary = "确认收货")
    public Result confirmOrder(@PathVariable("orderNo") String orderNo){
        orderService.confirmOrder(orderNo);
        return Result.success("确认收货成功");
    }
    @DeleteMapping("/{orderNo}")
    @Operation(summary = "删除订单")
    public Result deleteOrder(@PathVariable("orderNo") String orderNo){
        orderService.deleteOrder(orderNo);
        return Result.success("订单删除成功");
    }
}
