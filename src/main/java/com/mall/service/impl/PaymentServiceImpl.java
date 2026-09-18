package com.mall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.github.pagehelper.PageInfo;
import com.mall.common.BusinessException;
import com.mall.common.PageUtils;
import com.mall.common.Constants;
import com.mall.common.SnowflakeIdGenerator;
import com.mall.dto.AdminRefundDTO;
import com.mall.dto.PayCreateDTO;
import com.mall.dto.RefundDTO;
import com.mall.entity.Order;
import com.mall.entity.OrderItem;
import com.mall.entity.Payment;
import com.mall.entity.ProductSku;
import com.mall.entity.StockLog;
import com.mall.mapper.OrderItemMapper;
import com.mall.mapper.OrderMapper;
import com.mall.mapper.PaymentMapper;
import com.mall.mapper.ProductSkuMapper;
import com.mall.mapper.StockLogMapper;
import com.mall.service.IPaymentService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mall.service.PayChannelService;
import com.mall.util.SeckillStockRestorer;
import com.mall.util.SecurityUtils;
import com.mall.vo.AdminPaymentListVO;
import com.mall.vo.PageResult;
import com.mall.vo.PayCreateVO;
import com.mall.vo.PayParamsVO;
import com.mall.vo.PayResultVO;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static com.mall.enums.ErrorCode.ORDER_NOT_FOUND;
import static com.mall.enums.ErrorCode.ORDER_STATUS_ERROR;
import static com.mall.enums.ErrorCode.PAYMENT_NOT_FOUND;

/**
 * <p>
 * 支付流水表 服务实现类
 * </p>
 *
 * @author 乐乐
 * @since 2026-08-16
 */
@Service
public class PaymentServiceImpl extends ServiceImpl<PaymentMapper, Payment> implements IPaymentService {

    private final OrderMapper orderMapper;
    private final PayChannelService payChannelService;
    private final OrderItemMapper orderItemMapper;
    private final StockLogMapper stockLogMapper;
    private final ProductSkuMapper productSkuMapper;
    private final SnowflakeIdGenerator idGenerator;
    /** 秒杀库存回补：秒杀单全额退款时把秒杀活动库存（DB + Redis）还回去 */
    private final SeckillStockRestorer seckillStockRestorer;

    public PaymentServiceImpl(OrderMapper orderMapper,
                              PayChannelService payChannelService,
                              OrderItemMapper orderItemMapper,
                              StockLogMapper stockLogMapper,
                              ProductSkuMapper productSkuMapper,
            SnowflakeIdGenerator idGenerator,
            SeckillStockRestorer seckillStockRestorer) {
        this.orderMapper = orderMapper;
        this.payChannelService = payChannelService;
        this.orderItemMapper = orderItemMapper;
        this.stockLogMapper = stockLogMapper;
        this.productSkuMapper = productSkuMapper;
        this.idGenerator = idGenerator;
        this.seckillStockRestorer = seckillStockRestorer;
    }

    private String generatePaymentNo() {
        return "P" + idGenerator.nextId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PayCreateVO createPay(PayCreateDTO payCreateDTO) {
        Long userId = SecurityUtils.getUserId();

        // ① 校验订单：存在 + 属于当前用户（防越权，字段名修正为 user_id / order_no）
        Order order = orderMapper.selectOne(new QueryWrapper<Order>()
                .eq("user_id", userId)
                .eq("order_no", payCreateDTO.getOrderNo()));
        if (order == null) {
            throw new BusinessException(ORDER_NOT_FOUND);
        }

        // ② 校验订单状态：只有"待支付"才能发起支付
        if (order.getOrderStatus() != Constants.ORDER_STATUS_UNPAID) {
            throw new BusinessException(ORDER_STATUS_ERROR);
        }

        // ③ 幂等检查：该订单是否已有"待支付"的支付单，有则复用，不重复建单
        Payment exist = getOne(new QueryWrapper<Payment>()
                .eq("order_id", order.getId())
                .eq("pay_status", 0));
        if (exist != null) {
            return buildPayCreateVO(exist, order);
        }

        // ④ 生成支付单号 + 创建支付流水
        Payment payment = new Payment();
        payment.setPaymentNo(generatePaymentNo());
        payment.setOrderId(order.getId());
        payment.setOrderNo(order.getOrderNo());
        payment.setUserId(userId);
        payment.setAmount(order.getPayAmount());        // 金额从订单取，防前端篡改
        payment.setPayType(payCreateDTO.getPayType());
        payment.setPayStatus(0);                        // 0=待支付
        payment.setCreatedAt(LocalDateTime.now());
        payment.setUpdatedAt(LocalDateTime.now());
        save(payment);

        // ⑤ 生成支付参数（模拟支付 / 真实沙箱，通过 PayChannelService 抽象）
        String payParams = payChannelService.createPayParams(
                payment.getPaymentNo(),
                payment.getAmount(),
                "订单支付-" + order.getOrderNo()
        );

        // ⑥ 组装响应
        return buildPayCreateVO(payment, order, payParams);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handlePaySuccess(String paymentNo, String thirdTradeNo) {
        // ① 幂等抢占（CAS）：仅"待支付(0)"可翻转为已支付(1)。并发/重复回调只有一次影响行数为 1，
        //    其余在此处 0 行返回，后续更新不再执行——检查与写入在同一条 SQL 内完成，无竞态缝隙。
        int rows = this.baseMapper.markPaid(paymentNo, thirdTradeNo);
        if (rows == 0) {
            return;
        }
        Payment payment = getOne(new QueryWrapper<Payment>().eq("payment_no", paymentNo));
        if (payment == null) {
            throw new BusinessException(PAYMENT_NOT_FOUND);
        }

        // ② 订单状态翻转（CAS）：仅"待支付(0)"可翻转为已支付(1)。
        //    已取消（库存已回补）/已退款订单在此被拒绝——防止"取消后回填已支付"的超卖。
        int orderRows = orderMapper.markPaidById(payment.getOrderId());
        if (orderRows == 0) {
            throw new BusinessException(ORDER_STATUS_ERROR);
        }
    }

    /**
     * 假支付（演示用）：模拟"用户在收银台页面点击确认支付"这一动作。
     * 与真实回调的差异：真实回调由第三方服务器发起，靠验签建立信任；
     * 假支付由用户浏览器发起，靠登录态 + 归属校验建立信任——只允许支付自己的订单。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void mockPay(String paymentNo) {
        Long userId = SecurityUtils.getUserId();

        // ① 支付单必须存在且待支付（已支付/已退款不能重复支付）
        Payment payment = getOne(new QueryWrapper<Payment>().eq("payment_no", paymentNo));
        if (payment == null) {
            throw new BusinessException(PAYMENT_NOT_FOUND);
        }
        if (payment.getPayStatus() != Constants.PAY_STATUS_UNPAID) {
            throw new BusinessException(ORDER_STATUS_ERROR);
        }

        // ② 归属校验：只能支付自己的订单（防代付/越权）
        Order order = orderMapper.selectById(payment.getOrderId());
        if (order == null || !order.getUserId().equals(userId)) {
            throw new BusinessException(ORDER_NOT_FOUND);
        }
        if (order.getOrderStatus() != Constants.ORDER_STATUS_UNPAID) {
            throw new BusinessException(ORDER_STATUS_ERROR);
        }

        // ③ 走与真实回调完全相同的落账路径（幂等 CAS + 订单状态翻转）
        handlePaySuccess(paymentNo, "MOCK-" + System.currentTimeMillis());
    }

    @Override
    public PayResultVO getPayResult(String paymentNo) {
        Long userId = SecurityUtils.getUserId();

        // ① 越权校验：支付单必须属于当前用户
        Payment payment = getOne(new QueryWrapper<Payment>()
                .eq("user_id", userId)
                .eq("payment_no", paymentNo));
        if (payment == null) {
            throw new BusinessException(PAYMENT_NOT_FOUND);
        }

        // ② 拷贝同名字段（paymentNo/orderNo/amount/payType/payStatus/thirdTradeNo/paidAt）
        PayResultVO payResultVO = new PayResultVO();
        BeanUtils.copyProperties(payment, payResultVO);

        // ③ 状态文本转换（覆盖全部 4 种状态）
        payResultVO.setPayStatusText(statusToText(payment.getPayStatus()));

        return payResultVO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void refundOrder(RefundDTO refundDTO) {
        if (refundDTO == null || refundDTO.getOrderNo() == null) {
            throw new BusinessException(PAYMENT_NOT_FOUND);
        }
        Long userId = SecurityUtils.getUserId();
        // 用户端退款：必须属于当前用户（越权校验）
        doRefund(refundDTO.getOrderNo(), null, refundDTO.getReason(), userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void refundByAdmin(String orderNo, AdminRefundDTO dto) {
        // 后台退款：不限定 userId，可退任意用户订单；支持部分退款
        BigDecimal refundAmount = dto != null ? dto.getRefundAmount() : null;
        String reason = dto != null ? dto.getReason() : null;
        doRefund(orderNo, refundAmount, reason, null);
    }

    /**
     * 退款共用逻辑（Demo 简化：状态标记 + 回补库存 + 写流水）
     *
     * @param orderNo      订单号
     * @param refundAmount 退款金额（null=全额）
     * @param reason       退款原因
     * @param userId       用户ID（null 表示后台操作，不做越权过滤）
     */
    private void doRefund(String orderNo, BigDecimal refundAmount, String reason, Long userId) {
        // ① 校验订单：状态为已支付(1)或已发货(2)；后台不限定用户
        QueryWrapper<Order> orderWrapper = new QueryWrapper<Order>()
                .eq("order_no", orderNo)
                .in("order_status", Constants.ORDER_STATUS_PAID, Constants.ORDER_STATUS_SHIPPED);
        if (userId != null) {
            orderWrapper.eq("user_id", userId);
        }
        Order order = orderMapper.selectOne(orderWrapper);
        if (order == null) {
            throw new BusinessException(ORDER_NOT_FOUND);
        }

        // ② 查该订单的支付单（应为已支付状态）
        Payment payment = getOne(new QueryWrapper<Payment>()
                .eq("order_id", order.getId())
                .eq("pay_status", Constants.PAY_STATUS_PAID));
        if (payment == null) {
            throw new BusinessException(PAYMENT_NOT_FOUND);
        }

        // ③ 部分退款校验：退款金额不能大于已支付金额
        // 3. refund amount: positive, cumulative total must not exceed paid amount
        BigDecimal refundAmt = refundAmount == null ? payment.getAmount() : refundAmount;
        if (refundAmt.signum() <= 0) {
            throw new BusinessException(ORDER_STATUS_ERROR);
        }

        // 4. cumulative refund (CAS): only PAID(1) refundable; auto-flip to REFUNDED(3) when fully refunded.
        //    single UPDATE = cumulative check and write are atomic, concurrent refunds cannot over-refund.
        int refRows = this.baseMapper.applyRefund(payment.getPaymentNo(), refundAmt);
        if (refRows == 0) {
            throw new BusinessException(ORDER_STATUS_ERROR);
        }
        Payment freshPayment = getOne(new QueryWrapper<Payment>().eq("payment_no", payment.getPaymentNo()));

        // 5. only when fully refunded do we terminate the order and restore stock; partial refund records money only
        if (freshPayment.getPayStatus() == Constants.PAY_STATUS_REFUNDED) {
            int orderRows = orderMapper.markRefunded(order.getId());
            if (orderRows == 0) {
                throw new BusinessException(ORDER_STATUS_ERROR);
            }

            List<OrderItem> items = orderItemMapper.selectList(new QueryWrapper<OrderItem>()
                    .eq("order_id", order.getId()));
            for (OrderItem item : items) {
                int restored = productSkuMapper.restoreStock(item.getSkuId(), item.getQuantity());
                if (restored == 0) {
                    continue;
                }
                ProductSku fresh = productSkuMapper.selectById(item.getSkuId());
                int afterStock = fresh.getStock();
                int beforeStock = afterStock - item.getQuantity();

                StockLog stockLog = new StockLog();
                stockLog.setSkuId(item.getSkuId());
                stockLog.setOrderId(order.getId());
                stockLog.setOrderNo(order.getOrderNo());
                stockLog.setChangeType(Constants.STOCK_CHANGE_REFUND);
                stockLog.setChangeQty(item.getQuantity());
                stockLog.setBeforeStock(beforeStock);
                stockLog.setAfterStock(afterStock);
                stockLog.setRemark("退款回补");
                stockLog.setCreatedAt(LocalDateTime.now());
                stockLogMapper.insert(stockLog);
            }
            // 秒杀订单全额退款时，秒杀活动库存（DB + Redis）也要还回去——只退普通库存会让
            // 秒杀名额永久被这笔已退款订单占着。非秒杀单在组件第一道闸门就返回，普通订单零开销。
            seckillStockRestorer.restoreForSeckillOrder(order, items);
        }    }

    @Override
    public PageResult<AdminPaymentListVO> getAdminPayments(Integer pageNum, Integer pageSize,
                                                           String paymentNo, String orderNo,
                                                           Integer payStatus,
                                                           LocalDateTime startTime, LocalDateTime endTime) {
        // 1. 开启分页
        PageUtils.startPage(pageNum, pageSize);

        // 2. 多条件分页查询支付流水
        List<AdminPaymentListVO> voList = this.baseMapper.selectAdminPaymentList(
                paymentNo, orderNo, payStatus, startTime, endTime);

        // 3. 补支付状态文本
        voList.forEach(vo -> vo.setPayStatusText(statusToText(vo.getPayStatus())));

        // 4. 包装分页信息
        PageInfo<AdminPaymentListVO> pageInfo = new PageInfo<>(voList);
        return PageResult.of(pageInfo, voList);
    }

    /**
     * 支付状态 -> 文本
     */
    private String statusToText(Integer payStatus) {
        if (payStatus == null) {
            return "未知";
        }
        switch (payStatus) {
            case Constants.PAY_STATUS_UNPAID:   return "待支付";
            case Constants.PAY_STATUS_PAID:     return "支付成功";
            case Constants.PAY_STATUS_FAILED:   return "支付失败";
            case Constants.PAY_STATUS_REFUNDED: return "已退款";
            default:                            return "未知";
        }
    }

    /**
     * 组装发起支付响应
     */
    private PayCreateVO buildPayCreateVO(Payment payment, Order order) {
        return buildPayCreateVO(payment, order, null);
    }

    private PayCreateVO buildPayCreateVO(Payment payment, Order order, String payParams) {
        PayCreateVO vo = new PayCreateVO();
        vo.setPaymentNo(payment.getPaymentNo());
        vo.setOrderNo(payment.getOrderNo());
        vo.setAmount(payment.getAmount());
        vo.setPayType(payment.getPayType());
        vo.setPayStatus(payment.getPayStatus());

        if (payParams != null) {
            PayParamsVO params = new PayParamsVO();
            params.setAlipayTradePagePay(payParams);   // 复用该字段承载支付参数
            vo.setPayParams(params);
        }
        return vo;
    }
}
