package com.mall.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 订单金额汇总视图对象
 *
 * @author 乐乐
 */
@Data
public class OrderAmountVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 商品总金额 */
    private BigDecimal totalAmount;

    /** 运费 */
    private BigDecimal freightAmount;

    /** 应付金额 = 总金额 + 运费 */
    private BigDecimal payAmount;

}
