package com.mall.service;

import com.mall.dto.OrderCreateDTO;
import com.mall.entity.Order;
import com.baomidou.mybatisplus.extension.service.IService;
import com.mall.vo.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 * 订单主表 服务类
 * </p>
 *
 * @author 乐乐
 * @since 2026-08-16
 */
public interface IOrderService extends IService<Order> {

    OrderPreviewVO getOrderPreview();

    OrderCreateVO addOrder(OrderCreateDTO orderCreateDTO);

    /**
     * 订单分页列表
     *
     * @param pageNum     页码，从 1 开始
     * @param pageSize    每页条数
     * @param orderStatus 订单状态（可空，不传查全部）
     */
    PageResult<OrderListVO> getOrders(Integer pageNum, Integer pageSize, Integer orderStatus);

    OrderDetailVO getOrderDetail(String orderNo);

    void cancelOrder(String orderNo);

    void confirmOrder(String orderNo);

    void deleteOrder(String orderNo);
    /** 超时自动取消（定时任务）：CAS 翻转未支付→已取消并回补库存；订单已非待支付时静默跳过 */
    void cancelTimeout(Long orderId);

    /** 查询超时未支付订单（定时任务数据源） */
    List<Order> getTimeoutUnpaid(LocalDateTime deadline, int limit);

    /**
     * 管理员端订单分页列表（多条件查询）
     *
     * @param pageNum       页码
     * @param pageSize      每页条数
     * @param orderNo       订单号（可空）
     * @param userPhone     用户手机号（可空）
     * @param orderStatus   订单状态（可空）
     * @param paymentStatus 支付状态（可空）
     * @param startTime     下单时间起始（可空）
     * @param endTime       下单时间截止（可空）
     */
    PageResult<AdminOrderListVO> getAdminOrders(Integer pageNum, Integer pageSize,
                                                String orderNo, String userPhone,
                                                Integer orderStatus, Integer paymentStatus,
                                                LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 管理员端订单详情（后台可查任意用户订单）
     */
    OrderDetailVO getAdminOrderDetail(String orderNo);

    void sendOrder(String orderNo, String shippingCompany, String trackingNo);
}
