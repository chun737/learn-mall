package com.mall.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 购物车汇总视图对象
 *
 * @author 乐乐
 */
@Data
public class CartVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 全部商品总数量 */
    private Integer totalQuantity;

    /** 已勾选商品总数量 */
    private Integer checkedQuantity;

    /** 已勾选商品总金额 */
    private BigDecimal checkedAmount;

    /** 购物车项列表 */
    private List<CartItemVO> items;

}
