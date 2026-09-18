// 秒杀接口。对应后端 com.mall.controller.user.SeckkillController
// 注意路径是 /seckill（SecurityConfig 里 GET /seckill/** 是公开的，无需登录）

import request from './request'
import type { PageResult } from './product'

/** 后端 SeckillActivityVO */
export interface SeckillActivity {
  /** 秒杀活动 ID */
  id: number
  /** 活动名称 */
  activityName: string
  /** 商品 SPU ID（点进详情页要用它） */
  productId: number
  productName: string
  mainImage: string
  /** 秒杀 SKU ID */
  skuId: number
  specs: string
  /** 秒杀价 */
  seckillPrice: number
  /** 原价 */
  originalPrice: number
  totalStock: number
  /** 剩余库存（后端读 Redis 实时值） */
  availableStock: number
  /** 每人限购 */
  perLimit: number
  startTime: string
  endTime: string
  /** 活动状态：0=未开始 1=进行中 2=已结束 */
  activityStatus: number
  activityStatusText: string
  /** 服务器当前时间（前端据此算倒计时，避免客户端时间偏差） */
  serverTime: string
  /** 当前用户已购数量（详情接口才返回） */
  boughtQuantity?: number
}

/** 秒杀活动状态常量（与后端 Constants 对应） */
export const SECKILL_STATUS = {
  NOT_STARTED: 0,
  ONGOING: 1,
  ENDED: 2,
} as const

export interface SeckillQuery {
  /** 不传 = 全部 */
  status?: number
  pageNum?: number
  pageSize?: number
}

/** 秒杀活动列表：GET /seckill */
export function getSeckillList(params: SeckillQuery = {}) {
  return request.get<unknown, PageResult<SeckillActivity>>('/seckill', { params })
}
