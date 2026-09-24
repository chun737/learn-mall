package com.mall.mapper;

import com.mall.entity.Order;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mall.vo.AdminOrderListVO;
import com.mall.vo.OrderDetailVO;
import com.mall.vo.OrderListVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 * 订单主表 Mapper 接口
 * </p>
 *
 * @author 乐乐
 * @since 2026-08-16
 */
@Mapper
public interface OrderMapper extends BaseMapper<Order> {

    /**
     * 秒杀补偿前的订单存在性复核：显式包含逻辑删除行，避免误把已建单的历史订单当成未建单。
     */
    int countByOrderNoIncludingDeleted(@Param("orderNo") String orderNo);

    /**
     * 订单列表：联查 order_item，聚合出商品总数量 + 首条商品摘要
     * （配合 PageHelper 分页使用，SQL 本身不写 LIMIT）
     */
    List<OrderListVO> selectOrderList(@Param("userId") Long userId,
                                      @Param("orderStatus") Integer orderStatus);

    /**
     * 订单详情：一条 SQL 联查 order + order_item + payment
     * （带上 userId 做越权校验，防止查他人订单）
     */
    OrderDetailVO getOrderDetail(@Param("orderNo") String orderNo,
                                 @Param("userId") Long userId);

    /**
     * 管理员端订单详情：不带 userId 过滤（后台可看任意用户订单，含用户端已删除的）
     */
    OrderDetailVO getAdminOrderDetail(@Param("orderNo") String orderNo);

    /**
     * 管理员端订单列表：多条件分页查询（配合 PageHelper）
     *
     * @param orderNo       订单号（精确匹配，可空）
     * @param userPhone     下单用户手机号（可空，需 join user 表）
     * @param orderStatus   订单状态（可空）
     * @param paymentStatus 支付状态（可空）
     * @param startTime     下单时间起始（可空）
     * @param endTime       下单时间截止（可空）
     */
    List<AdminOrderListVO> selectAdminOrderList(@Param("orderNo") String orderNo,
                                                @Param("userPhone") String userPhone,
                                                @Param("orderStatus") Integer orderStatus,
                                                @Param("paymentStatus") Integer paymentStatus,
                                                @Param("startTime") LocalDateTime startTime,
                                                @Param("endTime") LocalDateTime endTime);

    /**
     * 支付成功翻转订单状态（CAS）：仅"待支付(0)"可翻转为"已支付(1)"。
     * 返回影响行数：0 = 订单已被取消/退款，禁止支付成功落账（防止超卖）。
     */
    int markPaidById(@Param("id") Long id);

    /**
     * 取消未支付订单（CAS）：仅"待支付(0)"可翻转为"已取消(4)"。
     * 返回影响行数：0 = 已被支付/取消/不存在（并发取消只有一次生效）。
     */
    int cancelUnpaid(@Param("orderNo") String orderNo, @Param("userId") Long userId);

    /**
     * 退款终止订单（CAS）：仅"已支付(1)/已发货(2)"可翻转为"已退款(5)"。
     */
    int markRefunded(@Param("id") Long id);

    /**
     * 定时任务数据源：超时未支付订单（分批限量）
     */
    List<Order> selectTimeoutUnpaid(@Param("deadline") LocalDateTime deadline, @Param("limit") int limit);

    /**
     * 系统取消未支付订单（CAS）：仅"待支付(0)"可翻转，与支付路径（markPaidById）互斥
     */
    int cancelUnpaidById(@Param("id") Long id);

    /**
     * 逻辑删除订单（CAS）：仅未删除的行生效。
     * 只 SET deleted/updated_at 两列，不做整行覆盖，避免把并发变更的其他列写回旧快照。
     */
    int markDeleted(@Param("id") Long id);

    /**
     * 发货（CAS）：仅"已支付(1)"可翻转为"已发货(2)"，物流信息与状态原子写入。
     * 返回影响行数：0 = 状态已被并发请求（退款/取消）翻转，拒绝覆盖（防丢失更新）。
     */
    int markShipped(@Param("orderNo") String orderNo,
                    @Param("company") String company,
                    @Param("trackingNo") String trackingNo);

    /**
     * 确认收货（CAS）：仅"已发货(2)"可翻转为"已完成(3)"。
     */
    int markCompleted(@Param("orderNo") String orderNo, @Param("userId") Long userId);
}
