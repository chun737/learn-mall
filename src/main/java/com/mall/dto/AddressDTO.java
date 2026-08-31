package com.mall.dto;

import lombok.Data;

/**
 * 收货地址新增/修改请求
 *
 * @author 乐乐
 */
@Data
public class AddressDTO {

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

    /** 详细地址（街道/门牌号） */
    private String detailAddress;

    /** 是否默认地址：0=否 1=是，默认 0 */
    private Integer isDefault;
}
