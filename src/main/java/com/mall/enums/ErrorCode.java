package com.mall.enums;

public enum ErrorCode {
    SUCCESS(0, "success"),

    // 通用
    PARAM_ERROR(400, "参数错误"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "无权限访问"),
    NOT_FOUND(404, "资源不存在"),
    SYSTEM_ERROR(500, "系统繁忙，请稍后重试"),
    // 用户
    USERNAME_EXIST(1001, "用户名已存在"),
    USER_NOT_FOUND(1002, "用户不存在"),
    PASSWORD_ERROR(1003, "用户名或密码错误"),
    USER_INFO_MISMATCH(1004, "用户名与手机号不匹配"),
    USER_LOCK(1009,"今日重试次数过多账号被锁定"),
    ACCOUNT_DISABLED(1008,"账号已被禁用"),

    // 角色
    ROLE_NOT_FOUND(1005, "角色不存在"),

    // 分类
    CATEGORY_NAME_EXIST(1006, "分类名称已存在"),
    CATEGORY_LEVEL_ERROR(1007, "分类层级非法（仅支持 1~3 级）"),
    CATEGORY_DELETE_FORBIDDEN(1008, "存在子分类或关联商品，无法删除"),

    // 商品/库存
    PRODUCT_OFF_SHELF(2001, "商品已下架"),
    STOCK_NOT_ENOUGH(2002, "库存不足"),
    PRODUCT_STATUS_ILLEGAL(2003, "非法的商品状态码"),
    PRODUCT_SKU_NOT_READY(2004, "请先添加并上架 SKU"),
    SKU_CODE_EXIST(2005, "SKU 编码已存在"),
    PRODUCT_NOT_ON_SHELF(2006, "商品未上架，无法上架 SKU"),

    // 订单/支付
    ORDER_NOT_FOUND(3001, "订单不存在"),
    ORDER_STATUS_ERROR(3002, "订单状态不允许该操作"),
    PAYMENT_FAILED(3003, "支付失败"),
    PAYMENT_NOT_FOUND(3004, "支付单不存在"),

    // 营销（api_doc 5.1：420xx 优惠券、421xx 秒杀）
    COUPON_RECEIVE_NOT_IN_TIME(42001, "优惠券不在可领取时间内"),
    COUPON_SOLD_OUT(42002, "优惠券已领完"),
    COUPON_LIMIT_REACHED(42003, "已达限领数量"),
    SECKILL_NOT_STARTED(42101, "秒杀活动未开始"),
    SECKILL_ENDED(42102, "秒杀活动已结束"),
    SECKILL_SOLD_OUT(42103, "秒杀库存不足"),
    SECKILL_LIMIT_REACHED(42104, "已达限购数量"),
    SECKILL_DUPLICATE(42105, "重复提交，请求处理中");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() { return code; }
    public String getMessage() { return message; }
}
