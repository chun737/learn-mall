// 库存相关（后台）：看板 + 库存流水
// 对应后端 admin/DashboardController（/admin/dashboard）、admin/StockLogController（/admin/stock-log）
//
// 看板是「汇总视图」，流水是「明细」——
// 看板上发现某个 SKU 库存异常时，直接带着它的 skuId 跳到流水页查原因。

import request from '../request'
import type { StockDashboardVO, StockLogVO, StockLogQuery, PageResult } from './types'

/** 库存看板：GET /admin/dashboard/stock（概览 + 预警列表 + 近 7 日趋势） */
export function getStockDashboard() {
  return request.get<unknown, StockDashboardVO>('/admin/dashboard/stock')
}

/**
 * 库存流水总览：GET /admin/stock-log
 * 支持按 SKU / 变更类型 / 订单号 / 时间区间筛选。
 */
export function listStockLogs(params: StockLogQuery) {
  return request.get<unknown, PageResult<StockLogVO>>('/admin/stock-log', { params })
}

/** 单个 SKU 的全部流水：GET /admin/stock-log/{skuId} */
export function listStockLogsBySku(
  skuId: number,
  params?: { pageNum?: number; pageSize?: number },
) {
  return request.get<unknown, PageResult<StockLogVO>>(`/admin/stock-log/${skuId}`, { params })
}
