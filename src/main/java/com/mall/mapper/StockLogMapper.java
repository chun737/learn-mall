package com.mall.mapper;

import com.mall.entity.StockLog;
import com.mall.vo.StockLogVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 * 库存扣减/回补流水表 Mapper 接口
 * </p>
 *
 * @author 乐乐
 * @since 2026-08-16
 */
@Mapper
public interface StockLogMapper extends BaseMapper<StockLog> {

    /**
     * 库存流水总览：多条件分页查询（配合 PageHelper）
     *
     * @param skuId        SKU ID（可空）
     * @param changeType   变更类型（可空）
     * @param orderNo      订单号（可空）
     * @param startTime    变更时间起始（可空）
     * @param endTime      变更时间截止（可空）
     */
    List<StockLogVO> selectStockLogList(@Param("skuId") Long skuId,
                                        @Param("changeType") Integer changeType,
                                        @Param("orderNo") String orderNo,
                                        @Param("startTime") LocalDateTime startTime,
                                        @Param("endTime") LocalDateTime endTime);

    /**
     * 单 SKU 库存流水：按 SKU ID 分页查询（配合 PageHelper）
     */
    List<StockLogVO> selectBySkuId(@Param("skuId") Long skuId);
}
