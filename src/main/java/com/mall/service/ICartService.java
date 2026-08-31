package com.mall.service;

import com.mall.entity.Cart;
import com.baomidou.mybatisplus.extension.service.IService;
import com.mall.vo.CartVO;

/**
 * <p>
 * 购物车表 服务类
 * </p>
 *
 * @author 乐乐
 * @since 2026-08-16
 */
public interface ICartService extends IService<Cart> {

    CartVO listCarts();

    void addCart(Integer skuId, Integer quantity);


    void modifyQ(Integer quantity, Integer id);

    void modifyC(Integer id, Integer checked);

    void modifyAll(Integer checked);

    void deleteC(Integer checked);

    void deleteCartById(Integer id);
}
