package com.mall.service;

import com.mall.dto.AdminRefundDTO;
import com.mall.dto.PayCreateDTO;
import com.mall.dto.RefundDTO;
import com.mall.entity.Payment;
import com.baomidou.mybatisplus.extension.service.IService;
import com.mall.vo.AdminPaymentListVO;
import com.mall.vo.PageResult;
import com.mall.vo.PayCreateVO;
import com.mall.vo.PayResultVO;

import java.time.LocalDateTime;

/**
 * <p>
 * 支付流水表 服务类
 * </p>
 *
 * @author 乐乐
 * @since 2026-08-16
 */
public interface IPaymentService extends IService<Payment> {

    PayCreateVO createPay(PayCreateDTO payCreateDTO);

    /**
     * 处理支付成功（模拟回调 / 真实第三方回调共用）
     *
     * @param paymentNo    支付单号
     * @param thirdTradeNo 第三方交易号（模拟时传入假值，真实时来自回调）
     */
    void handlePaySuccess(String paymentNo, String thirdTradeNo);

    /**
     * 假支付（演示用）：需登录，且只能支付当前用户自己的待支付订单
     */
    void mockPay(String paymentNo);

    PayResultVO getPayResult(String paymentNo);

    void refundOrder(RefundDTO refundDTO);

    /**
     * 后台退款（管理员专用）：不带 userId 越权校验，可退任意订单，支持部分退款
     *
     * @param orderNo 订单号
     */
    void refundByAdmin(String orderNo, AdminRefundDTO dto);

    /**
     * 后台支付流水分页查询（多条件）
     */
    PageResult<AdminPaymentListVO> getAdminPayments(Integer pageNum, Integer pageSize,
                                                    String paymentNo, String orderNo,
                                                    Integer payStatus,
                                                    LocalDateTime startTime, LocalDateTime endTime);
}
