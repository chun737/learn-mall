package com.mall.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import java.time.LocalDateTime;
import java.io.Serializable;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * <p>
 * 优惠券适用范围明细表
 * </p>
 *
 * @author 乐乐
 * @since 2026-08-27
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("coupon_scope")
public class CouponScope implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 优惠券模板 ID
     */
    @TableField("coupon_id")
    private Long couponId;

    /**
     * scope_type=1 时为 category.id；=2 时为 product.id
     */
    @TableField("target_id")
    private Long targetId;

    @TableField("created_at")
    private LocalDateTime createdAt;


}
