// 购物车接口。对应后端 com.mall.controller.user.CartController
// 全部需要登录（token 由 api/request.ts 的请求拦截器自动带上）

import request from './request'

/** 后端 CartItemVO：购物车里的一行 */
export interface CartItem {
  id: number
  productId: number
  productName: string
  mainImage: string
  skuId: number
  /** 规格描述（普通文本或 JSON 字符串，两种都要能显示） */
  specs: string
  skuImage: string
  /** SKU 实时售价 */
  price: number
  quantity: number
  /** 勾选状态：0=未勾选 1=已勾选 */
  checked: number
  /** 当前库存 */
  stock: number
}

/** 后端 CartVO：购物车汇总 + 明细 */
export interface CartVO {
  /** 全部商品总数量 */
  totalQuantity: number
  /** 已勾选商品总数量 */
  checkedQuantity: number
  /** 已勾选商品总金额 */
  checkedAmount: number
  items: CartItem[]
}

// ===== 查询 =====

/** 购物车列表：GET /cart */
export function getCart() {
  return request.get<unknown, CartVO>('/cart')
}

// ===== 增改 =====

/**
 * 加入购物车：POST /cart/items?skuId=&quantity=
 *
 * 注意后端签名是 @RequestParam（query 参数），不是请求体，
 * 所以这里第二个参数传 null（无 body），参数放在 config.params 里。
 */
export function addCart(skuId: number, quantity: number) {
  return request.post<unknown, string>('/cart/items', null, {
    params: { skuId, quantity },
  })
}

/** 修改数量：PUT /cart/items/{id}?quantity= */
export function updateCartQuantity(id: number, quantity: number) {
  return request.put<unknown, string>(`/cart/items/${id}`, null, {
    params: { quantity },
  })
}

/** 勾选 / 取消勾选单项：PUT /cart/items/{id}/checked?checked= */
export function updateCartChecked(id: number, checked: number) {
  return request.put<unknown, string>(`/cart/items/${id}/checked`, null, {
    params: { checked },
  })
}

/** 全选 / 取消全选：PUT /cart/checked?checked= */
export function updateCartCheckedAll(checked: number) {
  return request.put<unknown, string>('/cart/checked', null, {
    params: { checked },
  })
}

// ===== 删除 =====

/** ⭐ 清空购物车：DELETE /cart（无参数，清掉当前用户全部购物车项） */
export function clearCart() {
  return request.delete<unknown, string>('/cart')
}

/** 删除已勾选的项：DELETE /cart/checked */
export function removeCheckedCartItems() {
  return request.delete<unknown, string>('/cart/checked')
}

/** 删除单项：DELETE /cart/items/{id} */
export function removeCartItem(id: number) {
  return request.delete<unknown, string>(`/cart/items/${id}`)
}
