package com.mall.service.impl;

import com.github.pagehelper.PageInfo;
import com.mall.common.Constants;
import com.mall.common.PageUtils;
import com.mall.entity.StockLog;
import com.mall.mapper.ProductSkuMapper;
import com.mall.mapper.StockLogMapper;
import com.mall.service.IStockLogService;
import com.mall.vo.PageResult;
import com.mall.vo.StockDashboardVO;
import com.mall.vo.StockLogVO;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 * 库存扣减/回补流水表 服务实现类
 * </p>
 *
 * @author 乐乐
 * @since 2026-08-16
 */
@Service
public class StockLogServiceImpl extends ServiceImpl<StockLogMapper, StockLog> implements IStockLogService {

    private final ProductSkuMapper productSkuMapper;

    public StockLogServiceImpl(ProductSkuMapper productSkuMapper) {
        this.productSkuMapper = productSkuMapper;
    }

    @Override
    public StockDashboardVO getStockDashboard() {
        StockDashboardVO vo = new StockDashboardVO();

        // 1. 概览统计
        StockDashboardVO.StockSummary summary = productSkuMapper.selectStockSummary();
        vo.setSummary(summary != null ? summary : new StockDashboardVO.StockSummary());

        // 2. 库存预警列表
        vo.setLowStockList(productSkuMapper.selectLowStock());

        // 3. 近 7 日扣减趋势（下单扣减 change_type=1）
        LocalDateTime startTime = LocalDate.now().minusDays(6).atStartOfDay();
        vo.setDailyTrend(productSkuMapper.selectDailyTrend(startTime));

        return vo;
    }

    @Override
    public PageResult<StockLogVO> getStockLogList(Integer pageNum, Integer pageSize,
                                                  Long skuId, Integer changeType, String orderNo,
                                                  LocalDateTime startTime, LocalDateTime endTime) {
        PageUtils.startPage(pageNum, pageSize);
        List<StockLogVO> voList = this.baseMapper.selectStockLogList(
                skuId, changeType, orderNo, startTime, endTime);
        voList.forEach(vo -> vo.setChangeTypeText(changeTypeText(vo.getChangeType())));
        PageInfo<StockLogVO> pageInfo = new PageInfo<>(voList);
        return PageResult.of(pageInfo, voList);
    }

    @Override
    public PageResult<StockLogVO> getBySkuId(Integer pageNum, Integer pageSize, Long skuId) {
        PageUtils.startPage(pageNum, pageSize);
        List<StockLogVO> voList = this.baseMapper.selectBySkuId(skuId);
        voList.forEach(vo -> vo.setChangeTypeText(changeTypeText(vo.getChangeType())));
        PageInfo<StockLogVO> pageInfo = new PageInfo<>(voList);
        return PageResult.of(pageInfo, voList);
    }

    /**
     * 变更类型 -> 文本
     */
    private String changeTypeText(Integer changeType) {
        switch (changeType) {
            case Constants.STOCK_CHANGE_ORDER:
                return "下单扣减";
            case Constants.STOCK_CHANGE_CANCEL:
                return "取消回补";
            case Constants.STOCK_CHANGE_REFUND:
                return "退款回补";
            case Constants.STOCK_CHANGE_MANUAL:
                return "人工调整";
            default:
                return "未知";
        }
    }
}
