package com.mall.mapper;

import com.mall.entity.OrderItem;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

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

    /**
     * 批量插入订单明细：秒杀批量建单用，
     * 把 N 次单条插入的网络/日志固定开销摊薄成 1 次（SQL 在 XML 里 foreach 实现）
     */
    int insertBatch(@Param("list") List<OrderItem> items);
}
