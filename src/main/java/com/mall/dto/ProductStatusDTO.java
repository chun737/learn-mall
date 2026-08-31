package com.mall.dto;

import lombok.Data;

/**
 * 商品/SKU 上下架请求
 *
 * @author 乐乐
 */
@Data
public class ProductStatusDTO {

    /** 状态：商品 0=下架 1=上架；SKU 0=下架 1=上架 */
    private Integer status;
}
