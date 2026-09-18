// 订单接口。对应后端 com.mall.controller.user.OrderController
// 全部需要登录（token 由 api/request.ts 的请求拦截器自动带上）

import request from './request'
import type { Address } from './address'

/** 后端 OrderPreviewItemVO：结算清单的一项 */
export interface OrderPreviewItem {
  /** 购物车项 ID */
  cartId: number
  productId: number
  productName: string
  mainImage: string
  skuId: number
  specs: string
  /** 实时售价 */
  price: number
  quantity: number
  /** 当前库存 */
  stock: number
}

/** 后端 OrderAmountVO */
export interface OrderAmount {
  /** 商品总金额 */
  totalAmount: number
  /** 运费 */
  freightAmount: number
  /** 应付金额 = 总金额 + 运费 */
  payAmount: number
}

/**
 * 后端 OrderPreviewVO：下单预览，结算页的唯一数据源。
 * 注意 addresses 复用的就是收货地址模块的 AddressVO，所以可以直接用 Address 类型。
 */
export interface OrderPreview {
  addresses: Address[]
  /** 清单来自购物车【已勾选】的项 */
  items: OrderPreviewItem[]
  amount: OrderAmount
}

/** 后端 OrderSkuDTO：购买明细项 */
export interface OrderSku {
  skuId: number
  quantity: number
}

/** 后端 OrderCreateDTO（addressId 和 skuList 都是必填，后端有 @NotNull / @NotEmpty 校验） */
export interface OrderCreateParams {
  addressId: number
  remark?: string
  /** 用户优惠券 ID，本项目暂未做券页面，先不传 */
  couponId?: number
  /** 自定义订单号（幂等用），不传由后端生成 */
  orderNo?: string
  skuList: OrderSku[]
}

/** 后端 OrderCreateVO */
export interface OrderCreateResult {
  orderNo: string
  orderId: number
  /** 订单状态（0 待支付） */
  orderStatus: number
  payAmount: number
  createdAt: string
}

/**
 * 下单预览：GET /order/preview
 *
 * 无需传参——后端直接取当前用户购物车里【已勾选】的项来算，
 * 所以进结算页之前，购物车那边必须先有勾选。
 */
export function getOrderPreview() {
  return request.get<unknown, OrderPreview>('/order/preview')
}

/**
 * 创建订单：POST /order
 *
 * skuList 显式传入（而不是让后端去读购物车勾选项）：
 * 避免"用户点提交的瞬间购物车被改动"导致下错单，明细以页面所见为准。
 */
export function createOrder(params: OrderCreateParams) {
  return request.post<unknown, OrderCreateResult>('/order', params)
}

// ===== 订单状态（与后端 Constants.ORDER_STATUS_* 一致） =====

export const ORDER_STATUS = {
  /** 待支付 */
  UNPAID: 0,
  /** 已支付（待发货） */
  PAID: 1,
  /** 已发货（待收货） */
  SHIPPED: 2,
  /** 已完成 */
  COMPLETED: 3,
  /** 已取消 */
  CANCELLED: 4,
  /** 已退款 */
  REFUNDED: 5,
} as const

/** 后端 OrderListVO：订单列表的一行 */
export interface OrderListItem {
  orderNo: string
  orderStatus: number
  /** 后端已映射好的中文状态，前端不维护枚举 */
  orderStatusText: string
  paymentStatus: number
  payAmount: number
  /** 商品总数量 */
  totalQuantity: number
  /** 首条明细的商品图 */
  productImage: string
  /** 首条明细的商品名摘要 */
  productName: string
  createdAt: string
}

/** 后端 OrderDetailVO 里的收货信息快照 */
export interface OrderReceiver {
  receiverName: string
  receiverPhone: string
  province: string
  city: string
  district: string
  detailAddress: string
}

/** 后端 OrderItemVO：订单明细项 */
export interface OrderItem {
  productId: number
  productName: string
  mainImage: string
  skuId: number
  specs: string
  price: number
  quantity: number
}

/** 后端 OrderDetailVO */
export interface OrderDetail {
  orderNo: string
  orderStatus: number
  orderStatusText: string
  paymentStatus: number
  totalAmount: number
  freightAmount: number
  payAmount: number
  remark?: string
  receiver: OrderReceiver
  items: OrderItem[]
  paidAt?: string
  shippedAt?: string
  completedAt?: string
  createdAt: string
}

/**
 * 订单列表：GET /order?pageNum=&pageSize=&orderStatus=
 *
 * @param orderStatus 不传 = 查全部
 */
export function getOrders(pageNum = 1, pageSize = 10, orderStatus?: number) {
  return request.get<unknown, import('./product').PageResult<OrderListItem>>('/order', {
    params: { pageNum, pageSize, orderStatus },
  })
}

/** 订单详情：GET /order/{orderNo} */
export function getOrderDetail(orderNo: string) {
  return request.get<unknown, OrderDetail>(`/order/${orderNo}`)
}

/** 取消订单：PUT /order/{orderNo}/cancel（仅待支付可取消，后端会回补库存） */
export function cancelOrder(orderNo: string) {
  return request.put<unknown, string>(`/order/${orderNo}/cancel`)
}

/** 确认收货：PUT /order/{orderNo}（注意这个 PUT 没有子路径，语义是"确认收货"） */
export function confirmOrder(orderNo: string) {
  return request.put<unknown, string>(`/order/${orderNo}`)
}

/** 删除订单：DELETE /order/{orderNo} */
export function deleteOrder(orderNo: string) {
  return request.delete<unknown, string>(`/order/${orderNo}`)
}
