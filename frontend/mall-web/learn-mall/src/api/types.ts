/**
 * 全局类型与常量：所有 api 文件共用。
 *
 * 两条约定（与后端保持一致）：
 *   1. 类型名与后端 VO / DTO 一一对应，字段名**逐字对齐**（后端用驼峰，前端也用驼峰）；
 *   2. 状态码常量来自后端 com.mall.common.Constants，改一处要两边同步。
 */

// ==================== 通用结构 ====================

/** 后端统一响应壳：com.mall.common.Result */
export interface Result<T> {
  code: number
  message: string
  data: T
}

/** 后端分页结构：com.mall.vo.PageResult */
export interface PageResult<T> {
  list: T[]
  total: number
  pageNum: number
  pageSize: number
  totalPages: number
}

/** 分页查询的公共参数：所有列表接口都吃这三个（后端都有默认值 1 / 10） */
export interface PageQuery {
  pageNum?: number
  pageSize?: number
}

/** 带时间区间的查询：后台的订单/支付/库存流水都用这个 */
export interface TimeRangeQuery {
  /** 格式固定 yyyy-MM-dd HH:mm:ss（后端用 @DateTimeFormat 指定） */
  startTime?: string
  endTime?: string
}

// ==================== 状态常量 ====================
// 后端用 Integer 存状态，这里用 as const 保留字面量类型，
// 既能当枚举用（ORDER_STATUS.PAID），也能被 TS 推断出精确取值。

/** 订单状态（order.order_status） */
export const ORDER_STATUS = {
  /** 待支付 */
  UNPAID: 0,
  /** 已支付（待发货） */
  PAID: 1,
  /** 已发货（待收货） */
  SHIPPED: 2,
  /** 已完成 */
  COMPLETED: 3,
  /** 已取消 */
  CANCELLED: 4,
  /** 已退款 */
  REFUNDED: 5,
} as const

/** 订单的支付状态（order.payment_status） */
export const PAYMENT_STATUS = {
  UNPAID: 0,
  PAID: 1,
  REFUNDED: 2,
} as const

/** 支付流水状态（payment.pay_status）—— 注意比订单的多一个「失败」 */
export const PAY_STATUS = {
  UNPAID: 0,
  PAID: 1,
  FAILED: 2,
  REFUNDED: 3,
} as const

/** 商品状态 */
export const PRODUCT_STATUS = {
  OFF_SHELF: 0,
  ON_SHELF: 1,
  DRAFT: 2,
} as const

/** 用户状态 */
export const USER_STATUS = {
  DISABLED: 0,
  NORMAL: 1,
} as const

/** 优惠券类型 */
export const COUPON_TYPE = {
  /** 满减券 */
  FULL_REDUCTION: 1,
  /** 无门槛券 */
  NO_THRESHOLD: 2,
  /** 折扣券（后端未实现） */
  DISCOUNT: 3,
} as const

/** 优惠券模板状态（后台启停用） */
export const COUPON_STATUS = {
  DISABLED: 0,
  ENABLED: 1,
} as const

/** 用户手里那张券的状态 */
export const USER_COUPON_STATUS = {
  UNUSED: 0,
  USED: 1,
  EXPIRED: 2,
} as const

/** 秒杀活动状态 */
export const SECKILL_STATUS = {
  NOT_STARTED: 0,
  ONGOING: 1,
  ENDED: 2,
} as const

/** 秒杀下单结果（异步落库，前端要轮询） */
export const SECKILL_RESULT = {
  /** 排队中：还没生成订单，继续轮询 */
  QUEUING: 0,
  /** 成功：此时才有 orderNo */
  SUCCESS: 1,
} as const

/** 库存变更类型 */
export const STOCK_CHANGE_TYPE = {
  ORDER: 1,
  CANCEL: 2,
  REFUND: 3,
  MANUAL: 4,
} as const

// ==================== 用户端 · 认证 ====================

/** POST /auth/login 请求体 */
export interface LoginRequest {
  username: string
  password: string
}

/** POST /auth/login 返回 */
export interface LoginResponse {
  token: string
  userId: number
  username: string
}

/** POST /auth/register 请求体 */
export interface RegisterRequest {
  username: string
  password: string
  nickname?: string
  phone?: string
  email?: string
  gender?: number
}

/** POST /auth/register 返回（注册即登录，直接给 token） */
export interface RegisterResponse {
  id: number
  username: string
  nickname: string
  token: string
}

// ==================== 用户端 · 个人中心 ====================

/** GET /user/profile */
export interface UserProfileVO {
  id: number
  username: string
  nickname: string
  avatar: string
  phone: string
  email: string
  gender: number
  status: number
  lastLoginAt: string
  createdAt: string
}

/** PUT /user/profile 请求体 */
export interface UpdateProfileDTO {
  nickname?: string
  avatar?: string
  phone?: string
  email?: string
  gender?: number
}

/** PUT /user/password 请求体 */
export interface ChangePasswordDTO {
  oldPassword: string
  newPassword: string
}

/** PUT /user/password/forget 请求体（未登录可调） */
export interface ForgetPasswordDTO {
  username: string
  phone: string
  newPassword: string
}

// ==================== 用户端 · 收货地址 ====================

export interface AddressVO {
  id: number
  receiverName: string
  receiverPhone: string
  province: string
  city: string
  district: string
  detailAddress: string
  /** 1=默认地址 0=非默认 */
  isDefault: number
  createdAt: string
}

export interface AddressDTO {
  receiverName: string
  receiverPhone: string
  province: string
  city: string
  district: string
  detailAddress: string
  isDefault?: number
}

// ==================== 用户端 · 分类 ====================

/** GET /categories：树形结构，children 递归 */
export interface CategoryVO {
  id: number
  parentId: number
  name: string
  level: number
  icon: string
  children?: CategoryVO[]
}

// ==================== 用户端 · 商品 ====================

/** 列表行 */
export interface ProductListVO {
  id: number
  categoryId: number
  productName: string
  subTitle: string
  mainImage: string
  /** 该 SPU 下所有 SKU 的最低价 */
  minPrice: number
  maxPrice: number
  sales: number
}

/** SKU（详情页选规格用） */
export interface SkuVO {
  id: number
  skuCode: string
  /** 规格描述，可能是 JSON 字符串，展示前建议用 utils/text.ts 处理 */
  specs: string
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
  detail: string
  skus: SkuVO[]
}

/** 商品列表查询参数 */
export interface ProductQuery extends PageQuery {
  categoryId?: number
  keyword?: string
  /** 排序方式，后端默认 'default' */
  sortBy?: string
}

// ==================== 用户端 · 购物车 ====================

export interface CartItemVO {
  /** 购物车记录 id（不是 skuId，改数量/勾选/删除都用它） */
  id: number
  productId: number
  productName: string
  mainImage: string
  skuId: number
  specs: string
  skuImage: string
  price: number
  quantity: number
  /** 1=勾选 0=未勾选 */
  checked: number
  stock: number
}

export interface CartVO {
  totalQuantity: number
  checkedQuantity: number
  checkedAmount: number
  items: CartItemVO[]
}

// ==================== 用户端 · 订单 ====================

/** 下单预览里的一行商品 */
export interface OrderPreviewItemVO {
  cartId: number
  productId: number
  productName: string
  mainImage: string
  skuId: number
  specs: string
  price: number
  quantity: number
  stock: number
}

export interface OrderAmountVO {
  totalAmount: number
  freightAmount: number
  payAmount: number
}

export interface OrderPreviewVO {
  addresses: AddressVO[]
  items: OrderPreviewItemVO[]
  amount: OrderAmountVO
}

export interface OrderSkuDTO {
  skuId: number
  quantity: number
}

/** POST /order 请求体 */
export interface OrderCreateDTO {
  addressId: number
  remark?: string
  /** 使用的用户优惠券 id（user_coupon.id），不用券则不传 */
  couponId?: number
  /** 秒杀订单会带上来；普通下单不用传 */
  orderNo?: string
  skuList?: OrderSkuDTO[]
}

export interface OrderCreateVO {
  orderNo: string
  orderId: number
  orderStatus: number
  payAmount: number
  createdAt: string
}

/** 订单列表行 */
export interface OrderListVO {
  orderNo: string
  orderStatus: number
  orderStatusText: string
  paymentStatus: number
  payAmount: number
  totalQuantity: number
  productImage: string
  productName: string
  createdAt: string
}

export interface OrderReceiverVO {
  receiverName: string
  receiverPhone: string
  province: string
  city: string
  district: string
  detailAddress: string
}

export interface OrderItemVO {
  productId: number
  productName: string
  skuId: number
  skuSpecs: string
  skuImage: string
  price: number
  quantity: number
  totalAmount: number
}

/** 订单详情里的支付信息（可能为 null，未支付时） */
export interface OrderPaymentVO {
  paymentNo: string
  payType: number
  payStatus: number
  paidAt: string
}

export interface OrderDetailVO {
  orderNo: string
  orderStatus: number
  orderStatusText: string
  paymentStatus: number
  totalAmount: number
  freightAmount: number
  payAmount: number
  remark: string
  receiver: OrderReceiverVO
  items: OrderItemVO[]
  payment: OrderPaymentVO | null
  paidAt: string
  shippedAt: string
  completedAt: string
  createdAt: string
}

// ==================== 用户端 · 支付 ====================
//
// ⚠️ 支付接口的类型与常量**统一放在 `api/payment.ts`**（已投入使用，PayDialog.vue 依赖它）：
//      PAY_TYPES / PAY_STATUS / PayCreateParams / PayCreateResult / PayParams / PayResult
//      createPay() / mockPay() / getPayResult()
//    调用方请从 `@/api/payment` 导入，不要在这里再定义一套 —— 同一份数据两套类型，改的时候最容易漏。
//
// 这里只保留「订单详情里嵌套的支付信息」，它服务于订单接口，和上面的支付接口无关。

/** 订单详情（OrderDetailVO.payment）里嵌套的支付摘要 */
export interface OrderPaymentVO {
  paymentNo: string
  payType: number
  payStatus: number
  paidAt: string
}

// ==================== 用户端 · 优惠券 ====================

/** GET /coupon：可领取的券 */
export interface CouponVO {
  id: number
  couponName: string
  type: number
  typeText: string
  thresholdAmount: number
  discountAmount: number
  remainCount: number
  perLimit: number
  receiveStartTime: string
  receiveEndTime: string
  validDays: number
}

/** POST /coupon/{id}/receive 返回 */
export interface CouponReceiveVO {
  userCouponId: number
  couponId: number
  couponName: string
  couponStatus: number
  expireTime: string
}

/** GET /coupon/available：结算页可用券 */
export interface AvailableCouponVO {
  userCouponId: number
  couponName: string
  type: number
  thresholdAmount: number
  discountAmount: number
  expireTime: string
  /** 1=系统推荐的最优券，前端默认勾选它 */
  best: number
}

/** GET /user/coupons：我的券 */
export interface UserCouponVO {
  userCouponId: number
  couponId: number
  couponName: string
  type: number
  thresholdAmount: number
  discountAmount: number
  couponStatus: number
  couponStatusText: string
  receiveTime: string
  expireTime: string
  useTime: string
  orderNo: string
}

// ==================== 用户端 · 秒杀 ====================

export interface SeckillActivityVO {
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
  /** 服务端当前时间：前端算倒计时要基于它，避免本地时钟不准 */
  serverTime: string
  boughtQuantity: number
}

/** 秒杀下单 / 查询结果（异步：先排队，再轮询） */
export interface SeckillResultVO {
  seckillResult: number
  seckillResultText: string
  orderNo: string
  orderStatus: number
  payAmount: number
}

/** POST /seckill/{id}/orders 的参数 */
export interface SeckillOrderParams {
  addressId: number
  quantity?: number
  couponId?: number
}
