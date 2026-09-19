package com.mall.task;

import com.mall.entity.Order;
import com.mall.service.IOrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单超时自动取消任务（定时扫表方案）。
 *
 * - 与支付路径的竞态由 CAS 保证：cancelUnpaidById 与 markPaidById 同以 order_status=0 为条件，
 *   恰好一方生效，无需额外锁；
 * - 单笔失败不阻断本轮，订单仍是"未支付+已超时"，下一轮自然重试（自愈）；
 * - 多实例部署安全：CAS 保证同一订单只有一方真正取消并回补。
 *
 * @author 乐乐
 */
@Component
public class OrderTimeoutCancelTask {

    private static final Logger log = LoggerFactory.getLogger(OrderTimeoutCancelTask.class);

    /** 超时阈值：未支付超过 30 分钟自动取消 */
    private static final int TIMEOUT_MINUTES = 30;

    /** 单轮最多处理条数（防积压洪峰；处理不完下一轮继续） */
    private static final int BATCH_LIMIT = 100;

    private final IOrderService orderService;

    public OrderTimeoutCancelTask(IOrderService orderService) {
        this.orderService = orderService;
    }

    /** fixedDelay：上一轮跑完 30 秒后才开始下一轮，不重叠 */
    @Scheduled(fixedDelay = 30_000)
    public void cancelTimeoutOrders() {
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(TIMEOUT_MINUTES);
        List<Order> orders = orderService.getTimeoutUnpaid(deadline, BATCH_LIMIT);
        for (Order order : orders) {
            try {
                orderService.cancelTimeout(order);
            } catch (Exception e) {
                log.error("超时取消订单失败: orderNo={}", order.getOrderNo(), e);
            }
        }
    }
}
