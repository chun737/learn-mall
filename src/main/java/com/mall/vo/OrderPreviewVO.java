package com.mall.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 下单预览（结算页）视图对象
 *
 * @author 乐乐
 */
@Data
public class OrderPreviewVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 收货地址列表 */
    private List<AddressVO> addresses;

    /** 勾选商品清单 */
    private List<OrderPreviewItemVO> items;

    /** 金额汇总 */
    private OrderAmountVO amount;

}
