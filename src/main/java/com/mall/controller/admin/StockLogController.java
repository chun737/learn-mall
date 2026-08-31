package com.mall.controller.admin;

import com.mall.common.Result;
import com.mall.service.IStockLogService;
import com.mall.vo.PageResult;
import com.mall.vo.StockLogVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * 后台库存流水管理（管理员专用）
 *
 * @author 乐乐
 */
@RestController("AdminStockLogController")
@RequestMapping("/admin/stock-log")
@Tag(name = "后台库存流水", description = "库存流水查询")
public class StockLogController {

    private final IStockLogService stockLogService;

    public StockLogController(IStockLogService stockLogService) {
        this.stockLogService = stockLogService;
    }

    /**
     * 库存流水总览：多条件分页查询
     */
    @GetMapping()
    @Operation(summary = "库存流水总览", description = "按 SKU/变更类型/订单号/时间区间多条件分页查询")
    public Result<PageResult<StockLogVO>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Long skuId,
            @RequestParam(required = false) Integer changeType,
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        PageResult<StockLogVO> result = stockLogService.getStockLogList(
                pageNum, pageSize, skuId, changeType, orderNo, startTime, endTime);
        return Result.success(result);
    }

    /**
     * 单 SKU 库存流水：分页查询
     */
    @GetMapping("/{skuId}")
    @Operation(summary = "单 SKU 库存流水", description = "查询指定 SKU 的全部库存流水（分页）")
    public Result<PageResult<StockLogVO>> bySku(
            @PathVariable("skuId") Long skuId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        PageResult<StockLogVO> result = stockLogService.getBySkuId(pageNum, pageSize, skuId);
        return Result.success(result);
    }
}
