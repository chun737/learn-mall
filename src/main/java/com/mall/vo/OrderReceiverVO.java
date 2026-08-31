package com.mall.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 订单收货信息快照视图对象
 *
 * @author 乐乐
 */
@Data
public class OrderReceiverVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 收货人姓名 */
    private String receiverName;

    /** 收货人手机号 */
    private String receiverPhone;

    /** 省 */
    private String province;

    /** 市 */
    private String city;

    /** 区/县 */
    private String district;

    /** 详细地址 */
    private String detailAddress;

}
