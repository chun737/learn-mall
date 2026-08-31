package com.mall.controller.admin;

import com.mall.common.Result;
import com.mall.service.IPaymentService;
import com.mall.vo.AdminPaymentListVO;
import com.mall.vo.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * 后台支付流水管理（管理员专用）
 *
 * @author 乐乐
 */
@RestController("AdminPaymentController")
@RequestMapping("/admin/payment")
@Tag(name = "后台支付流水", description = "支付流水查询")
public class PaymentController {

    private final IPaymentService paymentService;

    public PaymentController(IPaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /**
     * 支付流水分页查询（多条件）
     */
    @GetMapping()
    @Operation(summary = "支付流水查询", description = "按支付单号/订单号/状态/时间区间多条件分页查询")
    public Result<PageResult<AdminPaymentListVO>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String paymentNo,
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) Integer payStatus,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        PageResult<AdminPaymentListVO> result = paymentService.getAdminPayments(
                pageNum, pageSize, paymentNo, orderNo, payStatus, startTime, endTime);
        return Result.success(result);
    }
}
