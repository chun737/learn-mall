package com.mall.mapper;

import com.mall.entity.Cart;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mall.vo.CartItemVO;
import com.mall.vo.OrderPreviewItemVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * <p>
 * 购物车表 Mapper 接口
 * </p>
 *
 * @author 乐乐
 * @since 2026-08-16
 */
@Mapper
public interface CartMapper extends BaseMapper<Cart> {


    void addCart(@Param("skuId")  Integer skuId,@Param("quantity")  Integer quantity,@Param("productId")  Long productId,@Param("userId") Long userId);

    List<CartItemVO> listCarts(@Param("userId") Long userId);
    @Update("update cart set checked = #{checked} where user_id = #{userId}")
    void updateByUserId(@Param("userId") Long userId, @Param("checked") Integer checked);

    List<OrderPreviewItemVO> selectCheckedItems(@Param("userId") Long userId);
}
