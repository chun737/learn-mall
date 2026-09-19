package com.mall.entity;

import java.math.BigDecimal;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
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
 * 商品 SKU 表（规格/价格/库存）
 * </p>
 *
 * @author 乐乐
 * @since 2026-08-16
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("product_sku")
public class ProductSku implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * SKU ID（主键）
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属商品ID（逻辑外键 -> product.id）
     */
    @TableField("product_id")
    private Long productId;

    /**
     * SKU 编码（唯一，如 IP15-256G-BLUE）
     */
    @TableField("sku_code")
    private String skuCode;

    /**
     * 规格描述（如 "256G 蓝色"）
     */
    @TableField("specs")
    private String specs;

    /**
     * 销售价格（元）
     */
    @TableField("price")
    private BigDecimal price;

    /**
     * 成本价格（元，用于毛利统计）
     */
    @TableField("cost_price")
    private BigDecimal costPrice;

    /**
     * 当前库存数量（并发扣减需配合行锁/乐观锁）
     */
    @TableField("stock")
    private Integer stock;

    /**
     * 累计销量（冗余统计，便于列表展示）
     */
    @TableField("sales")
    private Integer sales;

    /**
     * 库存预警阈值（当 stock < threshold 时触发预警，0 或 null 表示不预警）
     */
    @TableField("threshold")
    private Integer threshold;

    /**
     * SKU 图片 URL
     */
    @TableField("image")
    private String image;

    /**
     * SKU 状态：0=下架 1=上架
     */
    @TableField("status")
    private Integer status;

    /**
     * 创建时间
     */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @TableField("updated_at")
    private LocalDateTime updatedAt;

    /**
     * 逻辑删除：0=未删除 1=已删除
     */
    @TableLogic
    @TableField("deleted")
    private Integer deleted;


}
