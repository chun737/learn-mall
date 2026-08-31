package com.mall.mapper;

import com.mall.entity.ProductSku;
import com.mall.vo.StockDashboardVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 * 商品 SKU 表（规格/价格/库存） Mapper 接口
 * </p>
 *
 * @author 乐乐
 * @since 2026-08-16
 */
@Mapper
public interface ProductSkuMapper extends BaseMapper<ProductSku> {


    /**
     * 人工调整库存（条件扣减，防负库存）：
     * stock = stock + changeQty，仅当 stock + changeQty >= 0 才执行
     *
     * @return 影响行数：1 表示调整成功；0 表示库存不足或 SKU 不存在
     */
    int adjustStock(@Param("id") Long id, @Param("changeQty") Integer changeQty);

    /**
     * 库存看板-概览统计（总 SKU 数 / 总库存 / 预警数 / 上架数）
     */
    StockDashboardVO.StockSummary selectStockSummary();

    /**
     * 库存看板-预警列表（stock &lt; threshold 且 threshold &gt; 0）
     */
    List<StockDashboardVO.LowStockItem> selectLowStock();

    /**
     * 库存看板-近 N 日扣减趋势（change_type=1 下单扣减）
     */
    List<StockDashboardVO.TrendItem> selectDailyTrend(@Param("startTime") LocalDateTime startTime);

    /**
     * 原子扣减库存（CAS）：stock >= qty 才允许扣，行锁内完成判断与写入，杜绝超卖。
     * 返回影响行数：0 = 库存不足或 SKU 不存在。
     */
    int deductStock(@Param("skuId") Long skuId, @Param("qty") int qty);

    /**
     * 原子回补库存（取消/退款）：行内自增，销量回退并兜底不为负。
     */
    int restoreStock(@Param("skuId") Long skuId, @Param("qty") int qty);
}
