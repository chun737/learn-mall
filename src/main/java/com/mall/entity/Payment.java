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
 * 支付流水表
 * </p>
 *
 * @author 乐乐
 * @since 2026-08-16
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("payment")
public class Payment implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 支付流水ID（主键）
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 支付单号（唯一，业务幂等）
     */
    @TableField("payment_no")
    private String paymentNo;

    /**
     * 订单ID（逻辑外键 -> order.id）
     */
    @TableField("order_id")
    private Long orderId;

    /**
     * 订单号（冗余，便于对账）
     */
    @TableField("order_no")
    private String orderNo;

    /**
     * 支付用户ID（逻辑外键 -> user.id）
     */
    @TableField("user_id")
    private Long userId;

    /**
     * 支付金额（元）
     */
    @TableField("amount")
    private BigDecimal amount;

    /**
     * 支付方式：1=支付宝 2=微信 3=银行卡
     */
    @TableField("pay_type")
    private Integer payType;

    /**
     * 支付状态：0=待支付 1=支付成功 2=支付失败 3=已退款
     */
    @TableField("pay_status")
    private Integer payStatus;

    /**
     * 退款金额（部分退款时记录实际退了多少，缺省全额）
     */
    @TableField("refund_amount")
    private BigDecimal refundAmount;

    /**
     * 第三方支付平台交易号（回调时写入）
     */
    @TableField("third_trade_no")
    private String thirdTradeNo;

    /**
     * 第三方回调原始报文（JSON，便于排查与对账）
     */
    @TableField("callback_info")
    private String callbackInfo;

    /**
     * 支付成功时间
     */
    @TableField("paid_at")
    private LocalDateTime paidAt;

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


}
