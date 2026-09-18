// 商品后台管理（商品 + SKU + 图片上传）
// 对应后端 admin/ProductController、admin/ProductSkuController、admin/UploadController
//
// 为什么把 SKU 和商品放一个文件：
//   SKU 永远依附于某个商品（增删改都要 productId / skuId），
//   分两个文件反而要来回跳。上传接口只有后台用，也一并放这里。

import request from '../request'
import { uploadAdminImage } from '../upload'
import type {
  ProductListAdminVO,
  ProductDTO,
  AdminSkuVO,
  SkuDTO,
  StockAdjustDTO,
  SkuStatusDTO,
  AdminProductQuery,
  PageResult,
} from './types'
import type { ProductDetailVO } from '../types'

// ==================== 商品 ====================

/** 商品分页：GET /admin/product */
export function listProducts(params: AdminProductQuery) {
  return request.get<unknown, PageResult<ProductListAdminVO>>('/admin/product', { params })
}

/** 新增商品（可在同一次请求里带上 skus 数组一起建）：POST /admin/product */
export function createProduct(data: ProductDTO) {
  return request.post<unknown, ProductDetailVO>('/admin/product', data)
}

/**
 * 修改商品：PUT /admin/product/{id}
 * 传 skus 时后端按整体覆盖处理，注意别把不打算改的 SKU 漏掉。
 */
export function updateProduct(id: number, data: ProductDTO) {
  return request.put<unknown, string>(`/admin/product/${id}`, data)
}

/** 上架/下架：PUT /admin/product/{id}/status?status= （1=上架 0=下架） */
export function updateProductStatus(id: number, status: number) {
  return request.put<unknown, string>(`/admin/product/${id}/status`, null, { params: { status } })
}

/** 删除商品：DELETE /admin/product/{id} */
export function deleteProduct(id: number) {
  return request.delete<unknown, string>(`/admin/product/${id}`)
}

/** 商品下的 SKU 列表：GET /admin/product/{id}/skus */
export function listProductSkus(productId: number) {
  return request.get<unknown, AdminSkuVO[]>(`/admin/product/${productId}/skus`)
}

/** 给商品新增 SKU：POST /admin/product/{id}/skus */
export function addProductSku(productId: number, data: SkuDTO) {
  return request.post<unknown, string>(`/admin/product/${productId}/skus`, data)
}

// ==================== SKU ====================

/** 修改 SKU：PUT /admin/sku/{id} */
export function updateSku(id: number, data: SkuDTO) {
  return request.put<unknown, string>(`/admin/sku/${id}`, data)
}

/**
 * 调整库存：PUT /admin/sku/{id}/stock
 * @returns 调整后的最新库存数（后端直接返回 Integer，用来刷新表格这一格）
 *
 * changeQty 正数入库、负数出库，后端会写一条 stock_log 流水。
 */
export function adjustStock(id: number, data: StockAdjustDTO) {
  return request.put<unknown, number>(`/admin/sku/${id}/stock`, data)
}

/** SKU 上架/下架：PUT /admin/sku/{id}/status（body 里传 status） */
export function updateSkuStatus(id: number, status: number) {
  const body: SkuStatusDTO = { status }
  return request.put<unknown, string>(`/admin/sku/${id}/status`, body)
}

// ==================== 搜索同步 ====================

/**
 * ES 全量同步：POST /admin/product/es/import
 *
 * 把 MySQL 里全部在售商品灌进 ES 索引 product（幂等，重复调用会覆盖）。
 * 商品数据被批量改动过（比如直接改库）之后点一下这个按钮即可。
 */
export function importAllToEs() {
  return request.post<unknown, string>('/admin/product/es/import')
}

// ==================== 图片上传 ====================

export { uploadAdminImage as uploadImage }
