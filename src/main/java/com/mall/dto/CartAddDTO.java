package com.mall.dto;

import lombok.Data;

/**
 * 加入购物车请求
 *
 * @author 乐乐
 */
@Data
public class CartAddDTO {

    /** SKU ID */
    private Long skuId;

    /** 数量，1~99 */
    private Integer quantity;
}
