package com.mall.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 秒杀活动视图对象（前台列表 3.6.5 / 详情 3.6.6）
 *
 * @author 乐乐
 */
@Data
public class SeckillActivityVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 秒杀活动 ID */
    private Long id;

    /** 活动名称 */
    private String activityName;

    /** 商品 SPU ID */
    private Long productId;

    /** 商品名称 */
    private String productName;

    /** 商品主图 URL */
    private String mainImage;

    /** 秒杀 SKU ID */
    private Long skuId;

    /** 规格描述 */
    private String specs;

    /** 秒杀价 */
    private BigDecimal seckillPrice;

    /** 原价（SKU 当前售价） */
    private BigDecimal originalPrice;

    /** 秒杀总库存 */
    private Integer totalStock;

    /** 剩余秒杀库存（读 Redis 实时值，不经缓存） */
    private Integer availableStock;

    /** 每人限购数量 */
    private Integer perLimit;

    /** 开始时间 */
    private LocalDateTime startTime;

    /** 结束时间 */
    private LocalDateTime endTime;

    /** 活动状态：0=未开始 1=进行中 2=已结束（按时间自动流转） */
    private Integer activityStatus;

    /** 状态文本 */
    private String activityStatusText;

    /** 服务器当前时间（前端据此算倒计时，避免客户端时间偏差） */
    private LocalDateTime serverTime;

    /** 当前用户已购数量（详情接口返回，等于 perLimit 时前端置灰按钮） */
    private Integer boughtQuantity;
}
