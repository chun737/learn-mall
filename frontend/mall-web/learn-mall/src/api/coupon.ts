// 优惠券（用户端）
// 对应后端 user/CouponController（@RequestMapping("/coupon")）+ UserController 的 /user/coupons
//
// 三个场景分清楚：
//   1. 领券中心  —— GET /coupon            列出可领的券
//   2. 领券      —— POST /coupon/{id}/receive
//   3. 结算选券  —— GET /coupon/available?amount=  按订单金额算可用券
//   4. 我的券包  —— /user/coupons（在 user.ts 里）

import request from './request'
import type { CouponVO, CouponReceiveVO, AvailableCouponVO } from './types'

/**
 * 可领取的优惠券列表：GET /coupon
 * 不受登录限制（未登录也能看），但领取必须登录。
 */
export function listReceivableCoupons() {
  return request.get<unknown, CouponVO[]>('/coupon')
}

/**
 * 领取优惠券：POST /coupon/{id}/receive
 *
 * ⚠️ 用户身份由后端从 JWT 里取（SecurityUtils），前端**不要传 userId** —— 传了也不认。
 * 常见失败：已领过（超出每人限领）、已抢完、不在领取时间窗内，后端会返回具体 message。
 */
export function receiveCoupon(couponId: number) {
  return request.post<unknown, CouponReceiveVO>(`/coupon/${couponId}/receive`)
}

/**
 * 结算可用券：GET /coupon/available?amount=
 *
 * @param amount 结算商品总金额（后端按它过滤门槛，并给最优券打 best=1）
 *
 * 返回里 best=1 的那条建议默认勾选，用户可直接下单。
 */
export function listAvailableCoupons(amount: number) {
  return request.get<unknown, AvailableCouponVO[]>('/coupon/available', { params: { amount } })
}
