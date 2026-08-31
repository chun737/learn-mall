package com.mall.dto;

import lombok.Data;

/**
 * 调整 SKU 库存请求
 *
 * @author 乐乐
 */
@Data
public class StockAdjustDTO {

    /** 调整数量（正=增加，负=减少） */
    private Integer changeQty;

    /** 调整原因（如"盘点修正"） */
    private String remark;
}
