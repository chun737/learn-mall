package com.mall.service;

import com.mall.entity.StockLog;
import com.mall.vo.PageResult;
import com.mall.vo.StockDashboardVO;
import com.mall.vo.StockLogVO;
import com.baomidou.mybatisplus.extension.service.IService;

import java.time.LocalDateTime;

/**
 * <p>
 * 库存扣减/回补流水表 服务类
 * </p>
 *
 * @author 乐乐
 * @since 2026-08-16
 */
public interface IStockLogService extends IService<StockLog> {

    /**
     * 库存流水总览：多条件分页查询
     *
     * @param pageNum    页码
     * @param pageSize   每页条数
     * @param skuId      SKU ID（可空）
     * @param changeType 变更类型（可空）
     * @param orderNo    订单号（可空）
     * @param startTime  变更时间起始（可空）
     * @param endTime    变更时间截止（可空）
     */
    PageResult<StockLogVO> getStockLogList(Integer pageNum, Integer pageSize,
                                           Long skuId, Integer changeType, String orderNo,
                                           LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 单 SKU 库存流水：按 SKU ID 分页查询
     */
    PageResult<StockLogVO> getBySkuId(Integer pageNum, Integer pageSize, Long skuId);

    /**
     * 库存看板：总 SKU 数 + 库存预警列表 + 近 7 日扣减趋势
     */
    StockDashboardVO getStockDashboard();
}
