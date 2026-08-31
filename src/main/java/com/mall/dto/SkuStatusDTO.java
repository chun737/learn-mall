package com.mall.dto;

import lombok.Data;

/**
 * 修改 SKU 状态请求（上架/下架）
 *
 * @author 乐乐
 */
@Data
public class SkuStatusDTO {

    /** 状态：0=下架 1=上架 */
    private Integer status;
}
