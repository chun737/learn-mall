package com.mall.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 后台退款请求（管理员专用，Demo 简化直接退）
 *
 * @author 乐乐
 */
@Data
public class AdminRefundDTO {

    /** 退款金额，缺省全额 */
    private BigDecimal refundAmount;

    /** 退款原因 */
    private String reason;
}
