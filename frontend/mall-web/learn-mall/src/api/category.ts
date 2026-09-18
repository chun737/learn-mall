// 商品分类（用户端只读）
// 对应后端 user/CategoryController（@RequestMapping("/categories")）
//
// 后台的分类增删改在 admin/marketing.ts，两者路径不同：
//   用户端：/categories   （公开，不需要登录）
//   管理端：/admin/category（需 ADMIN）

import request from './request'
import type { CategoryVO } from './types'

/**
 * 分类树：GET /categories
 *
 * 返回的是**树形结构**（父分类的 children 里挂子分类），
 * 后端做了缓存，前端不用担心频繁调用。
 */
export function getCategoryTree() {
  return request.get<unknown, CategoryVO[]>('/categories')
}
