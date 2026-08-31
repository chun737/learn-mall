package com.mall.mapper;

import com.mall.entity.OrderItem;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * 订单明细表（商品快照） Mapper 接口
 * </p>
 *
 * @author 乐乐
 * @since 2026-08-16
 */
@Mapper
public interface OrderItemMapper extends BaseMapper<OrderItem> {

}
