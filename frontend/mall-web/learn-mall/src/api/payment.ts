// 支付接口。对应后端 com.mall.controller.user.PaymentController
// 全部需要登录（token 由 api/request.ts 的请求拦截器自动带上）

import request from './request'

/** 支付方式：值与后端约定一致（1=支付宝 2=微信 3=银行卡） */
export const PAY_TYPES = [
  { value: 1, label: '支付宝' },
  { value: 2, label: '微信支付' },
  { value: 3, label: '银行卡' },
] as const

/** 支付单状态：与后端 Constants 一致 */
export const PAY_STATUS = {
  UNPAID: 0,
  PAID: 1,
  FAILED: 2,
  REFUNDED: 3,
} as const

/** 后端 PayCreateDTO */
export interface PayCreateParams {
  orderNo: string
  /** 1=支付宝 2=微信 3=银行卡；不传后端默认 1 */
  payType?: number
  /** 支付完成后的同步跳转地址（前端页面），真实环境第三方会跳回来 */
  returnUrl?: string
}

/** 后端 PayParamsVO：第三方支付参数（真实环境用它跳转到对方收银台） */
export interface PayParams {
  alipayTradePagePay?: string
  wxPay?: string
}

/** 后端 PayCreateVO：发起支付的返回，核心是拿到 paymentNo */
export interface PayCreateResult {
  paymentNo: string
  orderNo: string
  amount: number
  payType: number
  /** 0=待支付 */
  payStatus: number
  payParams?: PayParams
}

/** 后端 PayResultVO：查询支付结果 */
export interface PayResult {
  paymentNo: string
  orderNo: string
  amount: number
  payType: number
  /** 0=待支付 1=成功 2=失败 3=已退款 */
  payStatus: number
  /** 后端已映射好的中文状态（如"支付成功"），前端不维护枚举 */
  payStatusText: string
  thirdTradeNo?: string
  paidAt?: string
}

/**
 * 发起支付：POST /payment
 *
 * 后端会建一条支付单并调支付渠道下单，返回 paymentNo；
 * 真实环境此时应拿 payParams.alipayTradePagePay 跳转到第三方收银台。
 */
export function createPay(params: PayCreateParams) {
  return request.post<unknown, PayCreateResult>('/payment', params)
}

/**
 * 模拟支付成功：POST /payment/mock/pay?paymentNo=
 *
 * 等价于"用户在第三方收银台点了确认付款"这个动作。
 * 本地没有真实的支付回调，用它把「下单 → 付款 → 订单变已支付」这条链跑通。
 * 后端会校验支付单归属当前用户，所以不是免登录的口子。
 */
export function mockPay(paymentNo: string) {
  return request.post<unknown, void>('/payment/mock/pay', null, {
    params: { paymentNo },
  })
}

/** 查询支付结果：GET /payment/{paymentNo} */
export function getPayResult(paymentNo: string) {
  return request.get<unknown, PayResult>(`/payment/${paymentNo}`)
}

/** 后端 RefundDTO：用户侧发起退款 */
export interface RefundParams {
  orderNo: string
  reason?: string
}

/**
 * 申请退款：POST /payment/refund
 *
 * 用户侧入口（订单列表/详情里的「申请退款」按钮）。
 * 后端会校验订单状态（已支付/已发货才可退），失败时 message 里是中文原因，直接提示即可。
 *
 * 注意与后台的 `PUT /admin/order/{orderNo}/refund` 区分：
 *   · 用户申请退款 = 本函数（只能退自己的单、全额）
 *   · 管理员退款   = admin 接口（可部分退款、可退任意用户的单）
 */
export function applyRefund(params: RefundParams) {
  return request.post<unknown, void>('/payment/refund', params)
}
