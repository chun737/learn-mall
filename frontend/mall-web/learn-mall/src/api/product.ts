// 接口定义文件：页面只调函数，不碰 URL。
// 一个 Controller 对应一个 api 文件，一个接口对应一个函数。
// 注意：下面写的是后端原始路径（/product）；axios 的 baseURL='/api' 会自动加前缀，
//       最终发给 vite 的是 /api/product，代理 rewrite 去掉 /api 后后端仍收到 /product。

import request from './request'

// 商品列表里的一行。字段名必须和后端 ProductListVO 的属性名完全一致
export interface ProductItem {
  id: number
  categoryId: number
  productName: string
  subTitle: string
  mainImage: string
  minPrice: number
  maxPrice: number
  sales: number
}

// 分页结果。泛型 <T> 表示"里面装的东西视接口而定"——图纸复用
export interface PageResult<T> {
  list: T[]
  total: number
  pageNum: number
  pageSize: number
  totalPages: number
}

// 搜索的查询条件。全部带 ? 表示可选——不传就取后端的默认值
export interface ProductQuery {
  pageNum?: number
  pageSize?: number
  keyword?: string
  sortBy?: string
}

// 搜索商品：GET /product?pageNum=&pageSize=&keyword=
export function searchProducts(params: ProductQuery) {
  return request.get<unknown, PageResult<ProductItem>>('/product', {
    params,
  })
}

// 搜索联想词：GET /product/suggest?keyword= → 返回热门搜索词列表
export function getSuggest(keyword: string) {
  return request.get<unknown, string[]>('/product/suggest', { params: { keyword } })
}

// ===== 商品详情相关类型（对应后端 ProductDetailVO / SkuVO） =====
export interface SkuVO {
  id: number
  skuCode: string
  specs: string          // 规格描述（JSON 字符串）
  price: number
  stock: number
  sales: number
  image: string
}

export interface ProductDetailVO {
  id: number
  categoryId: number
  productName: string
  subTitle: string
  mainImage: string
  detail: string         // 图文详情（纯文字）
  skus: SkuVO[]
}

// 商品详情：GET /product/{id}
export function getProductDetail(id: number) {
  return request.get<unknown, ProductDetailVO>(`/product/${id}`)
}
