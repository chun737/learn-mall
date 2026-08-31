package com.mall.controller.admin;

import com.mall.common.Result;
import com.mall.dto.AdminRefundDTO;
import com.mall.dto.ShipDTO;
import com.mall.service.IOrderService;
import com.mall.service.IPaymentService;
import com.mall.vo.AdminOrderListVO;
import com.mall.vo.OrderDetailVO;
import com.mall.vo.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController("AdminOrderController")
@RequestMapping("/admin/order")
@Tag(name = "管理员端订单管理")
public class OrderController {

    private final IOrderService orderService;
    private final IPaymentService paymentService;

    public OrderController(IOrderService orderService, IPaymentService paymentService) {
        this.orderService = orderService;
        this.paymentService = paymentService;
    }

    @GetMapping()
    @Operation(summary = "订单列表", description = "多条件分页查询订单")
    public Result<PageResult<AdminOrderListVO>> listOrders(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String userPhone,
            @RequestParam(required = false) Integer orderStatus,
            @RequestParam(required = false) Integer paymentStatus,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        PageResult<AdminOrderListVO> result = orderService.getAdminOrders(
                pageNum, pageSize, orderNo, userPhone, orderStatus, paymentStatus, startTime, endTime);
        return Result.success(result);
    }

    @GetMapping("/{orderNo}")
    @Operation(summary = "订单详情", description = "后台查看任意用户订单详情（含已删除订单）")
    public Result<OrderDetailVO> orderDetail(@PathVariable("orderNo") String orderNo) {
        return Result.success(orderService.getAdminOrderDetail(orderNo));
    }
    @PutMapping("/{orderNo}/ship")
    @Operation(summary = "订单发货", description = "已支付订单可发货，发货后状态变为已发货")
    public Result<?> sendOrder(@PathVariable("orderNo") String orderNo,
                               @RequestBody ShipDTO shipDTO){
        orderService.sendOrder(orderNo, shipDTO.getShippingCompany(), shipDTO.getTrackingNo());
        return Result.success("发货成功");
    }

    @PutMapping("/{orderNo}/refund")
    @Operation(summary = "后台退款", description = "Demo 简化直接退：已支付/已发货订单可退款，支持部分退款")
    public Result<?> refundOrder(@PathVariable("orderNo") String orderNo,
                                 @RequestBody(required = false) AdminRefundDTO dto) {
        paymentService.refundByAdmin(orderNo, dto);
        return Result.success("退款成功");
    }
}
