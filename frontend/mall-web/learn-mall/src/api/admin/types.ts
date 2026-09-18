/**
 * 管理端（/admin/**）专用类型。
 *
 * 通用结构（PageResult / PageQuery / TimeRangeQuery）和状态常量
 * 都在上一层的 `../types` 里，这里只放后台独有的 VO / DTO。
 */

import type { PageQuery, TimeRangeQuery } from '../types'

// 把跨端共用的类型再导出一次，方便后台页面只 import 这一个文件
export type { PageResult, PageQuery, TimeRangeQuery } from '../types'
export { ORDER_STATUS, PAYMENT_STATUS, PAY_STATUS, PRODUCT_STATUS, USER_STATUS, COUPON_TYPE, COUPON_STATUS, SECKILL_STATUS, STOCK_CHANGE_TYPE } from '../types'

// ==================== 用户管理 ====================

/** GET /admin/user 的列表行 */
export interface AdminUserVO {
  id: number
  username: string
  nickname: string
  phone: string
  email: string
  gender: number
  /** 1=正常 0=禁用 */
  status: number
  /** 角色编码列表，如 ['USER'] / ['ADMIN'] */
  roles: string[]
  lastLoginAt: string
  createdAt: string
}

/** POST /admin/user/register 请求体 */
export interface AdminUserDTO {
  username: string
  password: string
  nickname?: string
  phone?: string
  email?: string
  gender?: number
  status?: number
  /** 要绑定的角色编码，如 ['ADMIN'] */
  roleCodes?: string[]
}

/** 分配 / 移除角色（同一个结构，POST 是加，DELETE 是删） */
export interface UserRoleDTO {
  userId: number
  roleCode: string
}

// ==================== 分类管理 ====================

/** POST/PUT /admin/category 请求体 */
export interface CategoryDTO {
  parentId?: number
  name: string
  /** 层级：1=一级分类 2=二级 */
  level?: number
  sort?: number
  icon?: string
  /** 1=启用 0=停用 */
  status?: number
}

// ==================== 优惠券管理 ====================

/** GET /admin/coupon 列表行 */
export interface CouponAdminVO {
  id: number
  couponName: string
  type: number
  typeText: string
  thresholdAmount: number
  discountAmount: number
  /** 发行总量 */
  totalCount: number
  /** 已领取数 */
  receivedCount: number
  /** 已核销数 */
  usedCount: number
  /** 每人限领 */
  perLimit: number
  receiveStartTime: string
  receiveEndTime: string
  /** 领取后有效天数 */
  validDays: number
  /** 1=启用 0=停用 */
  status: number
  statusText: string
}

/** POST /admin/coupon 请求体 */
export interface CouponCreateDTO {
  couponName: string
  /** 1=满减 2=无门槛 */
  type: number
  thresholdAmount: number
  discountAmount: number
  totalCount: number
  perLimit: number
  receiveStartTime: string
  receiveEndTime: string
  validDays: number
}

/** GET /admin/coupon/{id}/records 列表行（领取/核销明细） */
export interface CouponRecordVO {
  userCouponId: number
  userId: number
  username: string
  couponStatus: number
  couponStatusText: string
  receiveTime: string
  useTime: string
  orderNo: string
}

// ==================== 商品与 SKU ====================

/** GET /admin/product 列表行（比用户端多了库存、SKU 数、状态） */
export interface ProductListAdminVO {
  id: number
  categoryId: number
  productName: string
  subTitle: string
  mainImage: string
  /** 该商品所有 SKU 库存之和 */
  stockTotal: number
  /** 0=下架 1=上架 2=草稿 */
  status: number
  statusText: string
  skuCount: number
  sales: number
  createdAt: string
}

export interface SkuDTO {
  skuCode: string
  /** 规格描述，如 {"颜色":"黑","内存":"256G"} 的 JSON 串 */
  specs: string
  price: number
  costPrice: number
  stock: number
  image: string
  status?: number
}

/** POST/PUT /admin/product 请求体（新增时可带 SKU 列表一起建） */
export interface ProductDTO {
  categoryId: number
  productName: string
  subTitle?: string
  mainImage?: string
  detail?: string
  status?: number
  skus?: SkuDTO[]
}

/** GET /admin/product/{id}/skus 列表行 */
export interface AdminSkuVO {
  id: number
  productId: number
  skuCode: string
  specs: string
  price: number
  costPrice: number
  stock: number
  sales: number
  image: string
  status: number
}

/** PUT /admin/sku/{id}/stock 请求体 */
export interface StockAdjustDTO {
  /** 变更量：正数=入库，负数=出库 */
  changeQty: number
  remark?: string
}

/** PUT /admin/sku/{id}/status 请求体 */
export interface SkuStatusDTO {
  status: number
}

// ==================== 订单与支付流水 ====================

/** GET /admin/order 列表行 */
export interface AdminOrderListVO {
  orderNo: string
  userId: number
  receiverName: string
  receiverPhone: string
  orderStatus: number
  orderStatusText: string
  paymentStatus: number
  payAmount: number
  itemCount: number
  createdAt: string
}

/** PUT /admin/order/{orderNo}/ship 请求体 */
export interface ShipDTO {
  shippingCompany: string
  trackingNo: string
}

/** PUT /admin/order/{orderNo}/refund 请求体（后端允许不传体，走全额退款） */
export interface AdminRefundDTO {
  /** 不传=全额退款 */
  refundAmount?: number
  reason?: string
}

/** GET /admin/payment 列表行 */
export interface AdminPaymentListVO {
  paymentNo: string
  orderNo: string
  userId: number
  amount: number
  payType: number
  payStatus: number
  payStatusText: string
  thirdTradeNo: string
  paidAt: string
}

// ==================== 秒杀管理 ====================

/** GET /admin/seckill 列表行（比用户端多了销量、Redis 库存、创建时间） */
export interface SeckillAdminVO {
  id: number
  activityName: string
  productId: number
  productName: string
  mainImage: string
  skuId: number
  specs: string
  seckillPrice: number
  originalPrice: number
  totalStock: number
  availableStock: number
  perLimit: number
  startTime: string
  endTime: string
  activityStatus: number
  activityStatusText: string
  serverTime: string
  boughtQuantity: number
  /** 已卖出的总数量 */
  soldQuantity: number
  /** Redis 里的实时库存（和 DB 可能瞬时不一致，用于排查） */
  redisStock: number
  createdAt: string
}

/** POST /admin/seckill 请求体 */
export interface SeckillCreateDTO {
  activityName: string
  skuId: number
  seckillPrice: number
  totalStock: number
  perLimit: number
  /** 格式 yyyy-MM-dd HH:mm:ss */
  startTime: string
  endTime: string
}

// ==================== 库存看板与流水 ====================

/** GET /admin/dashboard/stock */
export interface StockDashboardVO {
  summary: StockSummary
  /** 库存预警明细（stock < threshold） */
  lowStockList: LowStockItem[]
  /** 近 7 日扣减趋势 */
  dailyTrend: TrendItem[]
}

export interface StockSummary {
  totalSku: number
  totalStock: number
  lowStockCount: number
  onShelfSkuCount: number
}

export interface LowStockItem {
  skuId: number
  productId: number
  skuCode: string
  specs: string
  stock: number
  /** 预警阈值 */
  threshold: number
  /** 建议补货量 */
  suggestRestock: number
  price: number
}

export interface TrendItem {
  /** yyyy-MM-dd */
  date: string
  /** 当日扣减量 */
  deductQty: number
}

/** GET /admin/stock-log 列表行 */
export interface StockLogVO {
  id: number
  skuId: number
  orderNo: string
  /** 1=下单扣减 2=取消回补 3=退款回补 4=手动调整 */
  changeType: number
  changeTypeText: string
  /** 正数=增加，负数=扣减 */
  changeQty: number
  beforeStock: number
  afterStock: number
  remark: string
  createdAt: string
}

// ==================== 查询参数 ====================

/** GET /admin/user 查询参数 */
export interface AdminUserQuery extends PageQuery {
  /** 用户名 / 昵称 模糊搜索 */
  keyword?: string
}

/** GET /admin/product 查询参数 */
export interface AdminProductQuery extends PageQuery, TimeRangeQuery {
  categoryId?: number
  keyword?: string
  /** 0=下架 1=上架 2=草稿 */
  status?: number
}

/** GET /admin/order 查询参数 */
export interface AdminOrderQuery extends PageQuery, TimeRangeQuery {
  orderNo?: string
  /** 按下单人手机号搜索 */
  userPhone?: string
  orderStatus?: number
  paymentStatus?: number
}

/** GET /admin/payment 查询参数 */
export interface AdminPaymentQuery extends PageQuery, TimeRangeQuery {
  paymentNo?: string
  orderNo?: string
  payStatus?: number
}

/** GET /admin/stock-log 查询参数 */
export interface StockLogQuery extends PageQuery, TimeRangeQuery {
  skuId?: number
  changeType?: number
  orderNo?: string
}

/** GET /admin/coupon 查询参数 */
export interface AdminCouponQuery extends PageQuery {
  status?: number
  keyword?: string
}

/** GET /admin/seckill 查询参数 */
export interface AdminSeckillQuery extends PageQuery {
  /** 不传=全部 */
  status?: number
}
