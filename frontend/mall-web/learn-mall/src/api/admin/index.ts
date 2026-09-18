/**
 * 管理端 API 统一入口。
 *
 * 用法：
 *   import { adminApi } from '@/api/admin'
 *   const page = await adminApi.product.listProducts({ pageNum: 1, pageSize: 10 })
 *
 * 为什么用命名空间聚合而不是 `export *`：
 *   6 个模块里都有 list / create / update 这类同名函数，
 *   平铺导出会互相覆盖（TS 里表现为后面的覆盖前面，且不报错）。
 */

import * as auth from './auth'
import * as user from './user'
import * as product from './product'
import * as order from './order'
import * as marketing from './marketing'
import * as stock from './stock'

export const adminApi = {
  /** 登录 */
  auth,
  /** 用户管理 + 角色分配 */
  user,
  /** 商品 + SKU + 图片上传 + ES 同步 */
  product,
  /** 订单 + 支付流水 */
  order,
  /** 分类 + 优惠券 + 秒杀 */
  marketing,
  /** 库存看板 + 库存流水 */
  stock,
}

// 类型也一并导出，页面里直接用 adminApi 的类型时不用再找路径
export * from './types'
