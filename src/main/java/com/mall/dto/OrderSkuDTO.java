package com.mall.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 创建订单 - 购买明细项（skuList 元素）
 *
 * @author 乐乐
 */
@Data
public class OrderSkuDTO {

    /** SKU ID */
    @NotNull(message = "SKU ID 不能为空")
    private Long skuId;

    /** 数量，必须为正（负数/0 会穿透库存 CAS，反向加库存并生成负金额订单） */
    @NotNull(message = "数量不能为空")
    @Min(value = 1, message = "数量必须大于 0")
    private Integer quantity;
}
