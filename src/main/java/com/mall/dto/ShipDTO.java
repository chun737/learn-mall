package com.mall.dto;

import lombok.Data;

/**
 * 订单发货请求
 *
 * @author 乐乐
 */
@Data
public class ShipDTO {

    /** 物流公司（Demo 仅记录文案） */
    private String shippingCompany;

    /** 物流单号 */
    private String trackingNo;
}
