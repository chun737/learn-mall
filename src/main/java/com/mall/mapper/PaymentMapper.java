package com.mall.mapper;

import com.mall.entity.Payment;
import com.mall.vo.AdminPaymentListVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 * 支付流水表 Mapper 接口
 * </p>
 *
 * @author 乐乐
 * @since 2026-08-16
 */
@Mapper
public interface PaymentMapper extends BaseMapper<Payment> {

    /**
     * 后台支付流水分页查询（配合 PageHelper）
     *
     * @param paymentNo 支付单号（可空）
     * @param orderNo   订单号（可空）
     * @param payStatus 支付状态（可空）
     * @param startTime 支付时间起始（可空）
     * @param endTime   支付时间截止（可空）
     */
    List<AdminPaymentListVO> selectAdminPaymentList(@Param("paymentNo") String paymentNo,
                                                    @Param("orderNo") String orderNo,
                                                    @Param("payStatus") Integer payStatus,
                                                    @Param("startTime") LocalDateTime startTime,
                                                    @Param("endTime") LocalDateTime endTime);

    /**
     * 支付成功标记（CAS）：仅当支付单仍为"待支付(0)"时更新为已支付(1)。
     * 返回影响行数：1 = 本次回调抢到唯一一次成功；0 = 已被处理（重复回调，幂等返回）。
     */
    int markPaid(@Param("paymentNo") String paymentNo, @Param("thirdTradeNo") String thirdTradeNo);

    /**
     * 累计退款（CAS）：仅"已支付(1)"可退；累计退款不超过实付；累计退满时自动翻转为已退款(3)。
     * 返回影响行数：0 = 状态不允许或累计超额。
     */
    int applyRefund(@Param("paymentNo") String paymentNo, @Param("refundAmount") java.math.BigDecimal refundAmount);
}
