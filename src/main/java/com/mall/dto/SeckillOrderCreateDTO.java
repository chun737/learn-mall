package com.mall.dto;

import lombok.Data;

/**
 * 秒杀下单请求（前台 3.6.7）
 *
 * @author 乐乐
 */
@Data
public class SeckillOrderCreateDTO {

    /** 收货地址 ID（下单时拷贝为订单收货信息快照） */
    private Long addressId;

    /** 购买数量，默认 1，不得超过活动限购 perLimit */
    private Integer quantity;

    /** 用户优惠券 ID（user_coupon.id），秒杀价同样支持用券 */
    private Long couponId;
}
