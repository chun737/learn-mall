package com.mall.common;

import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;
import java.time.LocalDateTime;

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
    /*图片上传*/
    public static final Long  MAX_IMAGE_SIZE = 5 * 1024 * 1024L;

    // ---- 图片真实格式（魔数）识别 ----
    // 只校验扩展名挡不住"改名攻击"：把任意二进制文件命名成 evil.png 即可通过。
    // 这里以文件头魔数为准判定真实格式，登记表是「识别 + 白名单 + 派生元数据」的单一数据源。
    /** 各格式的文件头魔数（含偏移量）：{偏移，期望字节}；WebP 需要同时匹配 RIFF 与 WEBP 两段 */
    private static final int[][] MAGIC_JPEG = {{0, 0xFF}, {1, 0xD8}, {2, 0xFF}};
    private static final int[][] MAGIC_PNG  = {{0, 0x89}, {1, 0x50}, {2, 0x4E}, {3, 0x47},
                                              {4, 0x0D}, {5, 0x0A}, {6, 0x1A}, {7, 0x0A}};
    private static final int[][] MAGIC_GIF  = {{0, 'G'}, {1, 'I'}, {2, 'F'}, {3, '8'}};
    private static final int[][] MAGIC_WEBP = {{0, 'R'}, {1, 'I'}, {2, 'F'}, {3, 'F'},
                                              {8, 'W'}, {9, 'E'}, {10, 'B'}, {11, 'P'}};
    private static final int[][] MAGIC_BMP  = {{0, 'B'}, {1, 'M'}};
    /** 魔数签名探测所需的最小长度（webp 的 WEBP 段在偏移 8~11，取 12 覆盖全部格式） */
    private static final int MAGIC_PROBE_LENGTH = 12;
    /** 允许扩展名的别名 → 规范扩展名（jpeg 图常见两种写法；服务端统一存 jpg） */
    private static final java.util.Map<String, String> IMAGE_EXT_ALIAS = java.util.Map.of(
            "jpg", "jpg", "jpeg", "jpg", "png", "png", "gif", "gif", "webp", "webp", "bmp", "bmp");

    /** 全部允许的扩展名（含别名），用于错误提示文案 */
    public static final String ALLOWED_IMAGE_EXT_TEXT = String.join("/", IMAGE_EXT_ALIAS.keySet());

    /**
     * 按文件头魔数识别图片真实格式。
     * <p>以魔数为准而不是扩展名：改名成 .png 的任意文件在这里会被判为"不是图片"。
     *
     * @param head 文件头部字节；长度不足 {@link #MAGIC_PROBE_LENGTH} 时不可能是有效图片，返回 null
     * @return 规范扩展名（jpg/png/gif/webp/bmp）；无法识别为受支持图片时返回 null
     */
    public static String detectImageExt(byte[] head) {
        if (head == null || head.length < MAGIC_PROBE_LENGTH) {
            return null;
        }
        if (matches(head, MAGIC_JPEG)) return "jpg";
        if (matches(head, MAGIC_PNG))  return "png";
        if (matches(head, MAGIC_GIF))  return "gif";
        if (matches(head, MAGIC_WEBP)) return "webp";
        if (matches(head, MAGIC_BMP))  return "bmp";
        return null;
    }

    /** 把客户端传来的扩展名规范化为登记表里的写法（jpeg → jpg）；不在白名单返回 null */
    public static String normalizeImageExt(String ext) {
        return (ext == null) ? null : IMAGE_EXT_ALIAS.get(ext.trim().toLowerCase());
    }

    /** 规范扩展名 → 写入 OSS 对象元数据的 Content-Type；未知格式按二进制流处理 */
    public static String imageContentType(String canonicalExt) {
        if (canonicalExt == null) {
            return "application/octet-stream";
        }
        return switch (canonicalExt) {
            case "jpg"  -> "image/jpeg";
            case "png"  -> "image/png";
            case "gif"  -> "image/gif";
            case "webp" -> "image/webp";
            case "bmp"  -> "image/bmp";
            default     -> "application/octet-stream";
        };
    }

    /** 魔数比对：登记表里每个偏移都必须命中 */
    private static boolean matches(byte[] head, int[][] magic) {
        for (int[] pair : magic) {
            int offset = pair[0];
            if (offset >= head.length || (head[offset] & 0xFF) != pair[1]) {
                return false;
            }
        }
        return true;
    }
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
    /** 秒杀抢购结果 key 前缀（hash，field=userId，value=orderNo），完整 key = SECKILL_RESULT_PREFIX + activityId */
    public static final String SECKILL_RESULT_PREFIX = CACHE_PREFIX + "seckill:result:";

    // ---- 秒杀库存 / 已购 key 构造（Redis Cluster 适配）----
    // EVAL 多 KEYS 必须落在同一 slot（否则 CROSSSLOT），故用 {act:{activityId}} 哈希标签圈住活动 id，
    // 保证同活动的 stock / bought 两个 key 落在同一 slot；单机 Redis 行为等价，key 只是换了命名。
    /** 秒杀库存计数 key（Lua KEYS[1]）：seckill:{act:{id}}:stock */
    public static String seckillStockKey(Long activityId) {
        return "seckill:{act:" + activityId + "}:stock";
    }

    /** 秒杀已购数量 hash key（Lua KEYS[2]）：seckill:{act:{id}}:bought */
    public static String seckillBoughtKey(Long activityId) {
        return "seckill:{act:" + activityId + "}:bought";
    }
    /** 秒杀活动列表缓存 key（全量列表，60 秒短 TTL 应对状态时间流转） */
    public static final String SECKILL_LIST_KEY = CACHE_PREFIX + "seckill:list";
    /** 秒杀活动详情缓存 key 前缀，完整 key = SECKILL_DETAIL_KEY_PREFIX + activityId */
    public static final String SECKILL_DETAIL_KEY_PREFIX = CACHE_PREFIX + "seckill:detail:";
    /** 秒杀补偿幂等闸门 key 前缀：完整 key = SECKILL_COMPENSATED_PREFIX + orderNo，SETNX 抢到才有资格回补 */
    public static final String SECKILL_COMPENSATED_PREFIX = CACHE_PREFIX + "seckill:compensated:";
    /** 秒杀延迟待补偿 ZSET：member = activityId|userId|quantity|orderNo，score = 可补偿时间戳(ms) */
    public static final String SECKILL_COMPENSATE_PENDING_KEY = CACHE_PREFIX + "seckill:compensate:pending";

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
    /**
     * 秒杀库存/bought 两个 key 的存活余量：过期时刻 = 活动 end_time + 本缓冲。
     * <p>为什么要给它们设过期：原先这两个 key 是"永久 key"，活动自然结束后没人 DEL 就永久滞留，
     * bought 哈希还会随参与人数持续增长——活动越多、参与越多，Redis 内存只增不减。
     * <p>为什么加缓冲而不是严格按 end_time 过期：活动刚结束的瞬间仍可能有「回补库存」
     * （取消/超时/退款）与「对账读 redisStock」在跑，早过期会让这些操作读到"key 不存在"而跳过。
     */
    public static final Duration SECKILL_KEY_TTL_BUFFER = Duration.ofHours(2);
    /**
     * 兜底 TTL：活动的 end_time 为空时使用。
     * <p>用途是"时间字段不可信"时的安全网——宁可多留一天，也不能让 key 立刻消失导致进行中的活动被判为"未预热"而拒单。
     * <p>注意：活动"即将结束"（例如还剩 30 秒）不需要兜底，因为 {@code end_time + 缓冲} 本身就已经落在未来。
     */
    public static final Duration SECKILL_KEY_TTL_FALLBACK = Duration.ofHours(24);

    /**
     * 计算秒杀 stock / bought 两个 key 的存活时长，用于 {@code set(k, v, Duration)} / {@code setIfAbsent(k, v, Duration)}。
     * <p>语义是"从现在起，到活动结束时刻 + 缓冲 为止"还剩多久（相对时长，而非绝对时刻），
     * 因为 RedisTemplate 的写入 API 收的是 TTL 而不是过期时间点。
     *
     * <p>三种情形：
     * <ul>
     *   <li>正常活动 → {@code end_time + 缓冲}，与活动窗口对齐，活动结束后自动回收；</li>
     *   <li>创建时 end_time 已过期（历史活动被重新预热等）→ {@code end_time + 缓冲} 仍在未来，
     *       通常是"缓冲剩余量"，可接受；</li>
     *   <li>end_time 为 null（脏数据）→ 用兜底的 {@link #SECKILL_KEY_TTL_FALLBACK}。</li>
     * </ul>
     *
     * @param endTime 活动结束时间，可为 null
     * @return 相对存活时长，恒为正
     */
    public static Duration seckillKeyTtl(LocalDateTime endTime) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expireAt = (endTime == null)
                ? now.plus(SECKILL_KEY_TTL_FALLBACK)
                : endTime.plus(SECKILL_KEY_TTL_BUFFER);
        // 防御：万一算出来已过期（极端时钟回拨/脏数据），也要给一个正的下限，避免立刻过期把进行中的活动打挂
        return expireAt.isAfter(now) ? Duration.between(now, expireAt) : SECKILL_KEY_TTL_FALLBACK;
    }

    /**
     * 刷新已存在 key 的过期时间（不存在则什么都不做，<b>绝不创建 key</b>）。
     *
     * <p>专用于"回补/补偿"这类 {@code INCRBY} 写入之后：INCRBY 对不存在的 key 会创建一个<b>永久 key</b>，
     * 所以顺手把过期时间补上，避免秒杀 key 重新变成永久 key。
     * 这里不查活动的真实 end_time（补偿路径不该额外查库），统一用兜底 TTL——
     * key 若已存在，其过期时间本来就是"活动结束 + 缓冲"算出来的，这里只延长一点点，语义上仍然正确；
     * 若 key 恰好刚过期被重建，兜底 TTL 会限制它在有限时间内被回收，不会变成永久 key。
     *
     * @return true = 刷新了（key 本来存在）；false = key 不存在，未做任何操作
     */
    public static boolean refreshSeckillKeyTtl(RedisTemplate<String, Object> redisTemplate, String key) {
        if (!Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
            return false;
        }
        redisTemplate.expire(key, SECKILL_KEY_TTL_FALLBACK);
        return true;
    }

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
