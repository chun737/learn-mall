// 营销相关（后台）：分类 + 优惠券 + 秒杀
// 对应后端 admin/CategoryController、admin/CouponController、admin/SeckillController
//
// 三者都是「配置类」数据，操作模式高度相似（列表 / 新增 / 改 / 启停），放一起便于对照。

import request from '../request'
import type {
  CategoryDTO,
  CouponAdminVO,
  CouponCreateDTO,
  CouponRecordVO,
  AdminCouponQuery,
  SeckillAdminVO,
  SeckillCreateDTO,
  AdminSeckillQuery,
  PageResult,
} from './types'
import type { CategoryVO } from '../types'

// ==================== 分类 ====================

/** 分类列表（树）：GET /admin/category —— 比用户端多返回停用的分类 */
export function listCategories() {
  return request.get<unknown, CategoryVO[]>('/admin/category')
}

/** 新增分类：POST /admin/category */
export function createCategory(data: CategoryDTO) {
  return request.post<unknown, void>('/admin/category', data)
}

/** 修改分类：PUT /admin/category/{id} */
export function updateCategory(id: number, data: CategoryDTO) {
  return request.put<unknown, void>(`/admin/category/${id}`, data)
}

/**
 * 删除分类：DELETE /admin/category/{id}
 * 后端会校验「分类下是否还有商品」，有商品会拒绝并返回原因。
 */
export function deleteCategory(id: number) {
  return request.delete<unknown, void>(`/admin/category/${id}`)
}

// ==================== 优惠券 ====================

/** 优惠券分页：GET /admin/coupon */
export function listCoupons(params: AdminCouponQuery) {
  return request.get<unknown, PageResult<CouponAdminVO>>('/admin/coupon', { params })
}

/** 创建优惠券：POST /admin/coupon */
export function createCoupon(data: CouponCreateDTO) {
  return request.post<unknown, CouponAdminVO>('/admin/coupon', data)
}

/**
 * 启用/停用：PUT /admin/coupon/{id}/status?status=
 * 注意 status 是 **query 参数**（不是 body），这点和 SKU 的启停接口不同，容易写错。
 */
export function updateCouponStatus(id: number, status: number) {
  return request.put<unknown, string>(`/admin/coupon/${id}/status`, null, { params: { status } })
}

/** 领取/核销记录：GET /admin/coupon/{id}/records */
export function listCouponRecords(id: number, params?: { pageNum?: number; pageSize?: number }) {
  return request.get<unknown, PageResult<CouponRecordVO>>(`/admin/coupon/${id}/records`, { params })
}

// ==================== 秒杀 ====================

/** 秒杀活动分页：GET /admin/seckill（status 不传=全部，含未开始/已结束） */
export function listSeckills(params: AdminSeckillQuery) {
  return request.get<unknown, PageResult<SeckillAdminVO>>('/admin/seckill', { params })
}

/**
 * 创建秒杀活动：POST /admin/seckill
 * 后端会校验业务规则（价格、时间窗、库存）并**预热 Redis 库存**，
 * 所以创建成功立刻就能抢，不需要额外操作。
 */
export function createSeckill(data: SeckillCreateDTO) {
  return request.post<unknown, SeckillAdminVO>('/admin/seckill', data)
}

/** 终止活动：PUT /admin/seckill/{id}/terminate（提前结束，不可恢复） */
export function terminateSeckill(id: number) {
  return request.put<unknown, string>(`/admin/seckill/${id}/terminate`)
}
