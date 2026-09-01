package com.mall.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class SeckillOrderMessage implements Serializable {
    private Long activityId;   // 秒杀活动id
    private Long userId;       // 用户id
    private Long skuId;        // 商品SKU
    private Long addressId;    // 收货地址
    private Integer quantity;  // 数量
    private Long couponId;     // 优惠券
    private String orderNo;    // 订单号（先生成好，幂等用）
}
