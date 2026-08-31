package com.mall.dto;

import lombok.Data;

/**
 * 修改购物车商品数量请求
 *
 * @author 乐乐
 */
@Data
public class CartUpdateDTO {

    /** 新数量，1~99，不得大于当前库存 */
    private Integer quantity;
}
