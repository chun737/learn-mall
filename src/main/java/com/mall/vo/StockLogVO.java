package com.mall.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 库存流水视图对象
 *
 * @author 乐乐
 */
@Data
public class StockLogVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 流水 ID */
    private Long id;

    /** SKU ID */
    private Long skuId;

    /** 关联订单号 */
    private String orderNo;

    /** 变更类型：1=下单扣减 2=取消回补 3=退款回补 4=人工调整 */
    private Integer changeType;

    /** 变更类型文本 */
    private String changeTypeText;

    /** 变更数量（正/负） */
    private Integer changeQty;

    /** 变更前库存 */
    private Integer beforeStock;

    /** 变更后库存 */
    private Integer afterStock;

    /** 备注 */
    private String remark;

    /** 变更时间 */
    private LocalDateTime createdAt;

}
