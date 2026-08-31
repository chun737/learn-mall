package com.mall.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 收货地址视图对象
 *
 * @author 乐乐
 */
@Data
public class AddressVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 地址 ID */
    private Long id;

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

    /** 是否默认地址：0=否 1=是 */
    private Integer isDefault;

    /** 创建时间 */
    private LocalDateTime createdAt;

}
