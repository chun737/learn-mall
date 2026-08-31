package com.mall.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 秒杀下单 / 结果查询视图对象（前台 3.6.7 / 3.6.8）
 *
 * @author 乐乐
 */
@Data
public class SeckillResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 结果：0=排队中（异步模式） 1=下单成功 2=无抢购记录（结果查询） */
    private Integer seckillResult;

    /** 结果文本 */
    private String seckillResultText;

    /** 订单号，排队中/无记录为 null */
    private String orderNo;

    /** 订单状态（结果查询时返回，同订单状态枚举），无记录为 null */
    private Integer orderStatus;

    /** 应付金额 = 秒杀价 × 数量 - 优惠金额；下单成功时返回 */
    private BigDecimal payAmount;
}
