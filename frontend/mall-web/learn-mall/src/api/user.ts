// 个人中心：资料 / 密码 / 我的优惠券
// 对应后端 UserController（@RequestMapping("/user")）
//
// 注意：收货地址虽然也在 UserController 里，但已单独放在 address.ts，
//      这里只放"资料"和"券"两类，避免同一个文件越滚越大。

import request from './request'
import type {
  UserProfileVO,
  UpdateProfileDTO,
  ChangePasswordDTO,
  ForgetPasswordDTO,
  UserCouponVO,
  PageResult,
} from './types'

// ==================== 个人资料 ====================

/** 查询个人资料：GET /user/profile */
export function getProfile() {
  return request.get<unknown, UserProfileVO>('/user/profile')
}

/** 修改个人资料：PUT /user/profile（返回更新后的资料，可直接替换页面数据） */
export function updateProfile(data: UpdateProfileDTO) {
  return request.put<unknown, UserProfileVO>('/user/profile', data)
}

/**
 * 修改密码：PUT /user/password
 * 后端密码走请求体（不出现在 URL），改完要求重新登录 —— 调用方成功后就该清登录态跳登录页。
 */
export function changePassword(data: ChangePasswordDTO) {
  return request.put<unknown, string>('/user/password', data)
}

/**
 * 忘记密码：PUT /user/password/forget
 * 这个接口**未登录也能调**（后端 SecurityConfig 放行了它），
 * 校验依据是 username + phone，属于 Demo 的简化做法。
 */
export function forgetPassword(data: ForgetPasswordDTO) {
  return request.put<unknown, string>('/user/password/forget', data)
}

// ==================== 我的优惠券 ====================

/** 我的优惠券分页：GET /user/coupons?status=&pageNum=&pageSize= */
export function myCoupons(params?: {
  /** 0=未使用 1=已使用 2=已过期；不传=全部 */
  status?: number
  pageNum?: number
  pageSize?: number
}) {
  return request.get<unknown, PageResult<UserCouponVO>>('/user/coupons', { params })
}
