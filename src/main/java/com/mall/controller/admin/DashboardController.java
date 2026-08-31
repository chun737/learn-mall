package com.mall.controller.admin;

import com.mall.common.Result;
import com.mall.service.IStockLogService;
import com.mall.vo.StockDashboardVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台数据看板（管理员专用）
 *
 * @author 乐乐
 */
@RestController("AdminDashboardController")
@RequestMapping("/admin/dashboard")
@Tag(name = "后台数据看板", description = "库存看板等统计概览")
public class DashboardController {

    private final IStockLogService stockLogService;

    public DashboardController(IStockLogService stockLogService) {
        this.stockLogService = stockLogService;
    }

    /**
     * 库存看板：总 SKU 数 + 库存预警列表 + 近 7 日扣减趋势
     */
    @GetMapping("/stock")
    @Operation(summary = "库存看板", description = "返回库存概览、预警列表、近7日扣减趋势")
    public Result<StockDashboardVO> stockDashboard() {
        return Result.success(stockLogService.getStockDashboard());
    }
}
