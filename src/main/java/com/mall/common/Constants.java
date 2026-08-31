package com.mall.common;

import java.time.Duration;

/**
 * 全局常量类：集中管理项目中的魔法值，避免硬编码
 *
 * @author 乐乐
 */
public final class Constants {

    private Constants() {
        // 工具类，禁止实例化
    }

    // ==================== 角色 ====================
    /** 普通用户角色 */
    public static final String ROLE_USER = "USER";
    /** 管理员角色 */
    public static final String ROLE_ADMIN = "ADMIN";
    /** Spring Security 权限前缀 */
    public static final String ROLE_PREFIX = "ROLE_";

    // ==================== Redis ====================
    /** 登录 token 白名单 key 前缀 */
    public static final String LOGIN_TOKEN_PREFIX = "login:token:";
    /** 登录失败计数 key 前缀（按用户名，当日语义），完整 key = LOGIN_FAIL_PREFIX + username */
    public static final String LOGIN_FAIL_PREFIX = "mall:login:fail:";
    /** 单日最大登录失败次数，超过后锁定到当日结束 */
    public static final int MAX_LOGIN_ATTEMPTS = 5;
    /** 缓存统一 key 前缀（Spring Cache 与手写缓存共用，避免多项目共用 Redis 时键冲突） */
    public static final String CACHE_PREFIX = "mall:";
    /** 秒杀活动信息缓存 key 前缀（读多写少） */
    public static final String SECKILL_INFO_PREFIX = "seckill:info:";
    /** 秒杀剩余库存计数 key 前缀，完整 key = SECKILL_STOCK_PREFIX + activityId */
    public static final String SECKILL_STOCK_PREFIX = "seckill:stock:";
    /** 秒杀已购集合 key 前缀（Set，记录已购买用户，防限购超买），完整 key = SECKILL_BOUGHT_PREFIX + activityId */
    public static final String SECKILL_BOUGHT_PREFIX = "seckill:bought:";
    /** 秒杀抢购结果 key 前缀（hash，field=userId，value=orderNo），完整 key = SECKILL_RESULT_PREFIX + activityId */
    public static final String SECKILL_RESULT_PREFIX = CACHE_PREFIX + "seckill:result:";
    /** 秒杀活动列表缓存 key（全量列表，60 秒短 TTL 应对状态时间流转） */
    public static final String SECKILL_LIST_KEY = CACHE_PREFIX + "seckill:list";
    /** 秒杀活动详情缓存 key 前缀，完整 key = SECKILL_DETAIL_KEY_PREFIX + activityId */
    public static final String SECKILL_DETAIL_KEY_PREFIX = CACHE_PREFIX + "seckill:detail:";

    // ---- Spring Cache 缓存名（编译期常量，可直接用于 @Cacheable/@CacheEvict）----
    public static final String CACHE_NAME_CATEGORY = "category";
    public static final String CACHE_NAME_PRODUCT = "product";
    public static final String CACHE_NAME_PRODUCT_LIST = "productList";
    public static final String CACHE_NAME_HOT = "hot";

    // ---- 业务缓存 key ----
    /** 商品详情缓存 key 前缀，完整 key = CACHE_KEY_PRODUCT + 商品ID（与 Spring Cache 键格式一致，保证 @CacheEvict 联动） */
    public static final String CACHE_KEY_PRODUCT = CACHE_PREFIX + "product::";
    /** 商品详情互斥锁 key 前缀（防击穿），完整 key = LOCK_KEY_PRODUCT + 商品ID */
    public static final String LOCK_KEY_PRODUCT = CACHE_PREFIX + "lock:product::";
    /** 空值占位哨兵（防穿透：布隆放行但查库确认不存在时写入，非 null 字符串以便与"key 不存在"区分） */
    public static final String CACHE_NULL_PLACEHOLDER = "NULL";
    /** 商品 ID 布隆过滤器在 Redis 中的名称 */
    public static final String BLOOM_FILTER_PRODUCT = "mall:bloom:product";
    /** 可领优惠券列表整表缓存 key */
    public static final String CACHE_KEY_COUPON_RECEIVABLE = CACHE_PREFIX + "coupon:receivable";
    // ==================== 缓存 TTL ====================
    public static final Duration CACHE_TTL_CATEGORY = Duration.ofHours(1);
    public static final Duration CACHE_TTL_PRODUCT = Duration.ofMinutes(30);
    public static final Duration CACHE_TTL_PRODUCT_LIST = Duration.ofMinutes(5);
    public static final Duration CACHE_TTL_HOT = Duration.ofMinutes(15);
    /** 未在上方登记的缓存名的兜底 TTL */
    public static final Duration CACHE_TTL_DEFAULT = Duration.ofMinutes(30);
    /** 商品详情 TTL 随机抖动幅度（±，错开同类 key 到期时刻防雪崩） */
    public static final Duration CACHE_TTL_JITTER = Duration.ofMinutes(5);
    /** 空值占位 TTL（短：给"误判/删除后恢复"留快速自愈窗口） */
    public static final Duration CACHE_NULL_PLACEHOLDER_TTL = Duration.ofSeconds(60);
    /** 商品详情互斥锁 TTL（兜底持锁方宕机后的锁泄漏） */
    public static final Duration LOCK_TTL_PRODUCT = Duration.ofSeconds(10);
    public static final Duration TTL_COUPON_RECEIVABLE = Duration.ofMinutes(5);
    public static final Duration TTL_SECKILL_LIST = Duration.ofSeconds(60);
    public static final Duration TTL_SECKILL_DETAIL = Duration.ofSeconds(60);

    // ==================== 布隆过滤器参数 ====================
    /** 商品 ID 布隆过滤器预期元素量（宁大勿小：超出会推高误判率，只是多占内存） */
    public static final long BLOOM_EXPECTED_INSERTIONS = 100_000L;
    /** 目标误判率 1%（商品/秒杀共用） */
    public static final double BLOOM_FALSE_PROBABILITY = 0.01;
    /** 秒杀活动 ID 布隆过滤器在 Redis 中的名称 */
    public static final String BLOOM_FILTER_SECKILL = "mall:bloom:seckill";
    /** 秒杀布隆预期元素量：活动数远小于商品数，1 万已宽裕（内存约 12KB） */
    public static final long BLOOM_SECKILL_EXPECTED_INSERTIONS = 10_000L;

    // ==================== 分页 ====================
    /** 单页条数上限（防恶意大页拖垮查询） */
    public static final int MAX_PAGE_SIZE = 100;
    // ==================== 通用状态 ====================
    /** 未删除 */
    public static final int NOT_DELETED = 0;
    /** 已删除（逻辑删除） */
    public static final int DELETED = 1;

    // ==================== 用户 ====================
    /** 用户状态：正常 */
    public static final int USER_STATUS_NORMAL = 1;
    /** 用户状态：禁用 */
    public static final int USER_STATUS_DISABLED = 0;
    /** 性别：未知 */
    public static final int GENDER_UNKNOWN = 0;

    // ==================== 订单状态 ====================
    /** 待支付 */
    public static final int ORDER_STATUS_UNPAID = 0;
    /** 已支付（待发货） */
    public static final int ORDER_STATUS_PAID = 1;
    /** 已发货（待收货） */
    public static final int ORDER_STATUS_SHIPPED = 2;
    /** 已完成 */
    public static final int ORDER_STATUS_COMPLETED = 3;
    /** 已取消 */
    public static final int ORDER_STATUS_CANCELLED = 4;
    /** 已退款 */
    public static final int ORDER_STATUS_REFUNDED = 5;

    // ==================== 支付单状态 ====================
    /** 待支付 */
    public static final int PAY_STATUS_UNPAID = 0;
    /** 支付成功 */
    public static final int PAY_STATUS_PAID = 1;
    /** 支付失败 */
    public static final int PAY_STATUS_FAILED = 2;
    /** 已退款 */
    public static final int PAY_STATUS_REFUNDED = 3;

    // ==================== 订单支付状态 ====================
    /** 未支付 */
    public static final int PAYMENT_STATUS_UNPAID = 0;
    /** 已支付 */
    public static final int PAYMENT_STATUS_PAID = 1;
    /** 已退款 */
    public static final int PAYMENT_STATUS_REFUNDED = 2;

    // ==================== 库存流水类型 ====================
    /** 下单扣减 */
    public static final int STOCK_CHANGE_ORDER = 1;
    /** 取消回补 */
    public static final int STOCK_CHANGE_CANCEL = 2;
    /** 退款回补 */
    public static final int STOCK_CHANGE_REFUND = 3;
    /** 人工调整 */
    public static final int STOCK_CHANGE_MANUAL = 4;

    // ==================== 商品 / SKU 状态 ====================
    /** 商品状态：下架 */
    public static final int PRODUCT_STATUS_OFF_SHELF = 0;
    /** 商品状态：上架 */
    public static final int PRODUCT_STATUS_ON_SHELF = 1;
    /** 商品状态：草稿 */
    public static final int PRODUCT_STATUS_DRAFT = 2;

    // ==================== 收货地址 ====================
    /** 默认地址 */
    public static final int IS_DEFAULT = 1;
    /** 非默认地址 */
    public static final int NOT_DEFAULT = 0;

    // ==================== 购物车勾选 ====================
    /** 已勾选 */
    public static final int CHECKED = 1;
    /** 未勾选 */
    public static final int UNCHECKED = 0;

    // ==================== 优惠券类型 ====================
    /** 满减券 */
    public static final int COUPON_TYPE_FULL_REDUCTION = 1;
    /** 无门槛券 */
    public static final int COUPON_TYPE_NO_THRESHOLD = 2;
    /** 折扣券（扩展，Demo 不实现） */
    public static final int COUPON_TYPE_DISCOUNT = 3;

    // ==================== 优惠券模板状态 ====================
    /** 优惠券模板：停用 */
    public static final int COUPON_STATUS_DISABLED = 0;
    /** 优惠券模板：启用 */
    public static final int COUPON_STATUS_ENABLED = 1;

    // ==================== 用户持券状态 ====================
    /** 用户持券：未使用 */
    public static final int USER_COUPON_UNUSED = 0;
    /** 用户持券：已使用 */
    public static final int USER_COUPON_USED = 1;
    /** 用户持券：已过期 */
    public static final int USER_COUPON_EXPIRED = 2;

    // ==================== 秒杀活动状态 ====================
    /** 秒杀活动：未开始 */
    public static final int SECKILL_STATUS_NOT_STARTED = 0;
    /** 秒杀活动：进行中 */
    public static final int SECKILL_STATUS_ONGOING = 1;
    /** 秒杀活动：已结束 */
    public static final int SECKILL_STATUS_ENDED = 2;

    // ==================== 秒杀下单结果 ====================
    /** 秒杀下单：排队中（异步落库削峰） */
    public static final int SECKILL_RESULT_QUEUING = 0;
    /** 秒杀下单：成功（返回 orderNo） */
    public static final int SECKILL_RESULT_SUCCESS = 1;

    // ==================== 订单来源 ====================
    /** 普通订单 */
    public static final int ORDER_SOURCE_NORMAL = 0;
    /** 秒杀订单（取消/超时回补时需同步回补秒杀库存） */
    public static final int ORDER_SOURCE_SECKILL = 1;
}
