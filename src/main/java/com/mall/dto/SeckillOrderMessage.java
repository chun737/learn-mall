package com.mall.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class SeckillOrderMessage implements Serializable {
    private Long activityId;   // 秒杀活动id
    private Long userId;       // 用户id
    private Long skuId;        // 商品SKU
    private Long addressId;    // 收货地址
    private Integer quantity;  // 数量
    private Long couponId;     // 优惠券
    /**
     * 成交单价（活动秒杀价）。消费者按它结算，不能回退用 SKU 原价，
     * 否则秒杀价形同虚设（用户按原价付款）。生产端从活动 VO 取值随消息带过来，
     * 消费者无需再查活动表（批内 N 条消息仍是 0 次额外查询）。
     */
    private BigDecimal seckillPrice;
    private String orderNo;    // 订单号（先生成好，幂等用）
}
