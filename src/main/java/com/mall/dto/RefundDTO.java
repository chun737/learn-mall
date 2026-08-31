package com.mall.dto;

import lombok.Data;

/**
 * 申请退款请求
 *
 * @author 乐乐
 */
@Data
public class RefundDTO {

    /** 订单号 */
    private String orderNo;

    /** 退款原因 */
    private String reason;
}
