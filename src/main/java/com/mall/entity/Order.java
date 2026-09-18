package com.mall.entity;

import java.math.BigDecimal;

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
 * 订单主表
 * </p>
 *
 * @author 乐乐
 * @since 2026-08-16
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
// ⚠️ order 是 MySQL 保留字，表名必须用反引号包起来。
// MyBatis-Plus 的自动方法（insert / selectById / updateById…）会把这个值原样拼进 SQL，
// 不转义就会生成 "INSERT INTO order (...)" → BadSqlGrammarException: bad SQL grammar。
// 手写的 OrderMapper.xml 里一直写着 `order`，注解这里漏了，导致只有 MP 自动方法会挂。
@TableName("`order`")
public class Order implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 订单ID（主键）
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 订单号（唯一，业务幂等，如 20260815134402 + 随机/雪花）
     */
    @TableField("order_no")
    private String orderNo;

    /**
     * 下单用户ID（逻辑外键 -> user.id）
     */
    @TableField("user_id")
    private Long userId;

    /**
     * 订单总金额（商品金额）
     */
    @TableField("total_amount")
    private BigDecimal totalAmount;

    /**
     * 实付金额（含优惠/运费，Demo 暂不含营销）
     */
    @TableField("pay_amount")
    private BigDecimal payAmount;

    /**
     * 运费
     */
    @TableField("freight_amount")
    private BigDecimal freightAmount;

    /**
     * 订单状态：0=待支付 1=已支付 2=已发货 3=已完成 4=已取消 5=已退款
     */
    @TableField("order_status")
    private Integer orderStatus;

    /**
     * 支付状态：0=未支付 1=已支付 2=已退款
     */
    @TableField("payment_status")
    private Integer paymentStatus;

    /**
     * 收货人姓名（快照）
     */
    @TableField("receiver_name")
    private String receiverName;

    /**
     * 收货人手机号（快照）
     */
    @TableField("receiver_phone")
    private String receiverPhone;

    /**
     * 收货省（快照）
     */
    @TableField("receiver_province")
    private String receiverProvince;

    /**
     * 收货市（快照）
     */
    @TableField("receiver_city")
    private String receiverCity;

    /**
     * 收货区/县（快照）
     */
    @TableField("receiver_district")
    private String receiverDistrict;

    /**
     * 收货详细地址（快照）
     */
    @TableField("receiver_address")
    private String receiverAddress;

    /**
     * 订单备注（用户留言）
     */
    @TableField("remark")
    private String remark;

    /**
     * 支付时间
     */
    @TableField("paid_at")
    private LocalDateTime paidAt;

    /**
     * 物流公司（Demo 仅记录文案）
     */
    @TableField("shipping_company")
    private String shippingCompany;

    /**
     * 物流单号
     */
    @TableField("tracking_no")
    private String trackingNo;

    /**
     * 发货时间
     */
    @TableField("shipped_at")
    private LocalDateTime shippedAt;

    /**
     * 完成时间
     */
    @TableField("completed_at")
    private LocalDateTime completedAt;

    /**
     * 下单时间
     */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @TableField("updated_at")
    private LocalDateTime updatedAt;

    /**
     * 订单来源：0=普通订单 1=秒杀订单（取值见 Constants.ORDER_SOURCE_*）
     * <p>
     * 为什么必须标记：取消/超时/退款要判断「这笔单是否占用了秒杀活动库存」。
     * 详情页入口的商品也可能同时挂着活动，若不加标记，任何「SKU 恰好有活动、
     * 且下单时间落在活动时间窗内」的普通订单被取消时都会被误判成秒杀单，
     * 白白回补一次秒杀库存 → 秒杀库存虚增（超卖）。
     */
    @TableField("order_source")
    private Integer orderSource;

    /**
     * 逻辑删除：0=未删除 1=已删除（用户端删除，后台仍可查）
     */
    @TableField("deleted")
    private Integer deleted;


}
