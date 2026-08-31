package com.mall.dto;

import lombok.Data;

/**
 * 购物车勾选/取消勾选（含全选）请求
 *
 * @author 乐乐
 */
@Data
public class CartCheckedDTO {

    /** 0=取消勾选 1=勾选 */
    private Integer checked;
}
