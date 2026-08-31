package com.mall.controller.user;


import com.mall.common.Result;
import com.mall.dto.PayCreateDTO;
import com.mall.dto.RefundDTO;
import com.mall.service.IPaymentService;
import com.mall.vo.PayCreateVO;
import com.mall.vo.PayResultVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 支付流水表 前端控制器
 * </p>
 *
 * @author 乐乐
 * @since 2026-08-16
 */
@RestController("UserPaymentController")
@RequestMapping("/payment")
@Tag(name = "支付管理")
public class PaymentController {
    private final IPaymentService paymentService;

    public PaymentController(IPaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping()
    @Operation(summary = "发起支付")
    public Result<PayCreateVO> CreatePay(@RequestBody PayCreateDTO payCreateDTO){
        PayCreateVO payCreateVO = paymentService.createPay(payCreateDTO);
        return Result.success(payCreateVO);
    }

    /**
     * 假支付（演示用）：等价于"用户在收银台点击确认付款"。
     * 需要登录，且 Service 层校验只能支付自己的订单——不再是免登录的回调口子。
     */
    @PostMapping("/mock/pay")
    @Operation(summary = "模拟支付成功（演示）")
    public Result<Void> mockPay(@RequestParam String paymentNo){
        paymentService.mockPay(paymentNo);
        return Result.success();
    }
    @GetMapping("/{paymentNo}")
    @Operation(summary = "查询支付结果")
    public Result<PayResultVO> getPayResult(@PathVariable("paymentNo") String paymentNo){
        PayResultVO payResultVO = paymentService.getPayResult(paymentNo);
        return Result.success(payResultVO);
    }
    @PostMapping("/refund")
    @Operation(summary = "申请退款")
    public Result refundOrder(@RequestBody RefundDTO refundDTO){
        paymentService.refundOrder(refundDTO);
        return Result.success("退款申请成功");
    }
}
