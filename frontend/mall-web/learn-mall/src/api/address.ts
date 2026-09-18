// 收货地址接口。对应后端 com.mall.controller.user.UserController 的 /user/addresses 系列
// 全部需要登录（token 由 api/request.ts 的请求拦截器自动带上）

import request from './request'

/** 后端 AddressVO */
export interface Address {
  id: number
  receiverName: string
  receiverPhone: string
  province: string
  city: string
  district: string
  detailAddress: string
  /** 0=否 1=是 */
  isDefault: number
  createdAt?: string
}

/** 后端 AddressDTO：新增 / 修改的请求体 */
export interface AddressParams {
  receiverName: string
  receiverPhone: string
  province: string
  city: string
  district: string
  detailAddress: string
  /** 0=否 1=是，不传后端默认 0 */
  isDefault?: number
}

/** 地址列表：GET /user/addresses（注意返回的是数组，不是分页结构） */
export function listAddresses() {
  return request.get<unknown, Address[]>('/user/addresses')
}

/** 新增：POST /user/addresses */
export function addAddress(params: AddressParams) {
  return request.post<unknown, Address>('/user/addresses', params)
}

/** 修改：PUT /user/addresses/{id} */
export function updateAddress(id: number, params: AddressParams) {
  return request.put<unknown, string>(`/user/addresses/${id}`, params)
}

/** 删除：DELETE /user/addresses/{id} */
export function deleteAddress(id: number) {
  return request.delete<unknown, string>(`/user/addresses/${id}`)
}

/** 设为默认：PUT /user/addresses/{id}/default */
export function setDefaultAddress(id: number) {
  return request.put<unknown, string>(`/user/addresses/${id}/default`)
}
