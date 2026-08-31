package com.mall.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 创建订单请求
 *
 * @author 乐乐
 */
@Data
public class OrderCreateDTO {

    /** 收货地址 ID（下单时拷贝为订单收货信息快照） */
    @NotNull(message = "收货地址不能为空")
    private Long addressId;

    /** 订单备注 */
    private String remark;

    /** 用户优惠券 ID（user_coupon.id），传入则在下单事务内核销（api_doc 3.4.2） */
    private Long couponId;

    /** 自定义订单号（幂等用），缺省由后端生成 */
    private String orderNo;

    /** 购买明细（优先于购物车勾选项；不传则取购物车勾选项） */
    @NotEmpty(message = "购买明细不能为空")
    @Valid
    private List<OrderSkuDTO> skuList;
}
