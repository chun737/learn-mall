package com.mall.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 库存看板视图对象（可选扩展）
 *
 * @author 乐乐
 */
@Data
public class StockDashboardVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 概览统计 */
    private StockSummary summary;

    /** 库存预警列表（stock < threshold） */
    private List<LowStockItem> lowStockList;

    /** 近 7 日库存扣减趋势 */
    private List<TrendItem> dailyTrend;

    /**
     * 库存概览
     */
    @Data
    public static class StockSummary implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 总 SKU 数 */
        private Long totalSku;
        /** 总库存量 */
        private Long totalStock;
        /** 预警 SKU 数 */
        private Long lowStockCount;
        /** 上架 SKU 数 */
        private Long onShelfSkuCount;
    }

    /**
     * 预警明细
     */
    @Data
    public static class LowStockItem implements Serializable {
        private static final long serialVersionUID = 1L;
        /** SKU ID */
        private Long skuId;
        /** 所属商品 ID */
        private Long productId;
        /** SKU 编码 */
        private String skuCode;
        /** 规格 */
        private String specs;
        /** 当前库存 */
        private Integer stock;
        /** 预警阈值 */
        private Integer threshold;
        /** 建议补货量（threshold - stock，负值归 0） */
        private Integer suggestRestock;
        /** SKU 售价 */
        private BigDecimal price;
    }

    /**
     * 每日趋势
     */
    @Data
    public static class TrendItem implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 日期（yyyy-MM-dd） */
        private String date;
        /** 当日扣减量（change_type=1） */
        private Long deductQty;
    }
}
