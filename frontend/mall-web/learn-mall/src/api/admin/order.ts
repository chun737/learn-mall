// 订单与支付流水（后台）
// 对应后端 admin/OrderController（/admin/order）、admin/PaymentController（/admin/payment）
//
// 后台订单详情复用的是用户端同一个 OrderDetailVO（多了「可查已删除订单」的能力），
// 所以类型从 ../types 引入，不要重复定义。

import request from '../request'
import type {
  AdminOrderListVO,
  AdminOrderQuery,
  ShipDTO,
  AdminRefundDTO,
  AdminPaymentListVO,
  AdminPaymentQuery,
  PageResult,
} from './types'
import type { OrderDetailVO } from '../types'

// ==================== 订单 ====================

/**
 * 订单分页：GET /admin/order
 *
 * 支持 8 个条件：订单号、下单人手机号、订单状态、支付状态、时间区间 + 分页。
 * 时间格式固定 yyyy-MM-dd HH:mm:ss（后端用 @DateTimeFormat 指定），
 * 用 Element Plus 的 date-picker 时记得把 value-format 设成一样的。
 */
export function listOrders(params: AdminOrderQuery) {
  return request.get<unknown, PageResult<AdminOrderListVO>>('/admin/order', { params })
}

/** 订单详情（含已删除订单）：GET /admin/order/{orderNo} */
export function getOrderDetail(orderNo: string) {
  return request.get<unknown, OrderDetailVO>(`/admin/order/${orderNo}`)
}

/** 发货：PUT /admin/order/{orderNo}/ship（只有「已支付」状态可发货） */
export function shipOrder(orderNo: string, data: ShipDTO) {
  return request.put<unknown, string>(`/admin/order/${orderNo}/ship`, data)
}

/**
 * 后台退款：PUT /admin/order/{orderNo}/refund
 *
 * 不传请求体 = 全额退款；传 refundAmount 则部分退款。
 * 后端对 body 标了 required = false，所以这里允许 data 为空。
 */
export function refundOrder(orderNo: string, data?: AdminRefundDTO) {
  return request.put<unknown, string>(`/admin/order/${orderNo}/refund`, data ?? null)
}

// ==================== 支付流水 ====================

/** 支付流水分页：GET /admin/payment（按支付单号/订单号/状态/时间区间） */
export function listPayments(params: AdminPaymentQuery) {
  return request.get<unknown, PageResult<AdminPaymentListVO>>('/admin/payment', { params })
}
