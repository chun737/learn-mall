package com.mall.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单列表项视图对象（用户端）
 *
 * @author 乐乐
 */
@Data
public class OrderListVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 订单号 */
    private String orderNo;

    /** 订单状态 */
    private Integer orderStatus;

    /** 订单状态文本 */
    private String orderStatusText;

    /** 支付状态 */
    private Integer paymentStatus;

    /** 应付金额 */
    private BigDecimal payAmount;

    /** 商品总数量 */
    private Integer totalQuantity;

    /** 首条明细商品图 */
    private String productImage;

    /** 首条明细商品名摘要 */
    private String productName;

    /** 下单时间 */
    private LocalDateTime createdAt;

}
