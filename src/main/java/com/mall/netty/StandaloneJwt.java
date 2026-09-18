package com.mall.netty;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 独立进程版 JWT 校验（Netty 教程 7.3 · 方案 B）。
 *
 * <h3>为什么不直接用 com.mall.util.JwtUtil</h3>
 * JwtUtil 是 Spring Bean，密钥靠 {@code @Value("${jwt.secret}")} 注入 —— 只有 Spring 容器
 * 启动后才有值。而 ChatServer 跑在<b>独立 JVM 进程</b>（9090）里，那边不会启动 Spring，
 * 注入永远不发生。静态桥接（原 NettySpringBridge）拿到的永远是 null，导致真实 token
 * 100% 被拒（教程 7.1 的 P0 问题）。
 *
 * <h3>密钥从哪来（按优先级）</h3>
 * <ol>
 *   <li>JVM 参数：{@code -Djwt.secret=xxx}</li>
 *   <li>环境变量：{@code JWT_SECRET=xxx}</li>
 *   <li>{@code application.yaml} 里的 {@code jwt.secret}（自动解析 {@code ${ENV:默认值}} 占位符）</li>
 * </ol>
 *
 * <h3>密钥必须与 Spring 侧完全一致</h3>
 * 两边验的是同一批 token。密钥不同 → 签名验不过 → 所有真实用户被拒。
 * Spring 侧读的是 {@code ${JWT_SECRET}}，所以只要保证环境变量一致即可。
 */
public final class StandaloneJwt {

    private static final String PROP_KEY = "jwt.secret";
    private static final String ENV_KEY = "JWT_SECRET";

    /** 解析结果：这条 token 是谁、什么角色 */
    public record Auth(Long userId, String role) {}

    /** 缓存的密钥；null 表示还没解析成功 */
    private static volatile SecretKey key;
    /** 密钥来源描述，仅用于启动日志排查 */
    private static volatile String keySource = "未找到";
    /** 没配密钥只提醒一次，别把日志刷爆 */
    private static volatile boolean missingKeyWarned = false;

    private StandaloneJwt() {}

    // ======================= 对外：解析 token =======================

    /**
     * 解析 token。
     *
     * @return 校验通过返回 {@link Auth}；token 为空、过期、签名不对、没配密钥，一律返回 null
     */
    public static Auth parseUser(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        SecretKey secretKey = key();
        if (secretKey == null) {
            return null;                       // 原因已由 warnMissingKey() 打印过
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            Long userId = Long.valueOf(claims.getSubject());
            List<?> roles = claims.get("roles", List.class);
            String role = (roles != null && !roles.isEmpty()) ? String.valueOf(roles.get(0)) : "USER";
            return new Auth(userId, role);
        } catch (Exception e) {
            // 这里必须打印具体原因 —— 早先的版本把 NPE 静默吞掉，排查起来毫无线索
            System.out.println("[Netty] token 校验失败: " + e.getClass().getSimpleName()
                    + " - " + e.getMessage());
            return null;
        }
    }

    /** 密钥是否已就绪，供启动横幅显示 */
    public static boolean isReady() {
        return key() != null;
    }

    /** 密钥来源描述，供启动横幅显示 */
    public static String keySource() {
        key();                                 // 触发一次解析，保证 keySource 有值
        return keySource;
    }

    // ======================= 密钥解析 =======================

    private static SecretKey key() {
        SecretKey cached = key;
        if (cached != null) {
            return cached;
        }
        synchronized (StandaloneJwt.class) {
            if (key != null) {
                return key;
            }
            String secret = findSecret();
            if (secret == null) {
                warnMissingKey();
                return null;
            }
            try {
                // HS256 要求密钥 >= 32 字节（256 bit），太短这里会抛异常
                key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
                return key;
            } catch (Exception e) {
                System.out.println("[Netty] JWT 密钥不可用（长度需 >= 32 字节）: " + e.getMessage());
                return null;
            }
        }
    }

    /** 统一走 {@link AppConfig} 读配置：-D 参数 → 环境变量 → application.yaml */
    private static String findSecret() {
        String fromProp = System.getProperty(PROP_KEY);
        if (fromProp != null && !fromProp.isBlank()) {
            keySource = "JVM 参数 -Djwt.secret";
            return fromProp.trim();
        }
        String fromEnv = AppConfig.env(ENV_KEY);
        if (fromEnv != null) {
            keySource = "环境变量 " + ENV_KEY;
            return fromEnv;
        }
        String fromYaml = AppConfig.get("jwt.secret");
        if (fromYaml != null) {
            keySource = "application.yaml 的 jwt.secret";
            return fromYaml;
        }
        return null;
    }

    private static void warnMissingKey() {
        if (missingKeyWarned) {
            return;
        }
        missingKeyWarned = true;
        keySource = "未找到";
        System.out.println("""
                [Netty] ⚠ 未找到 JWT 密钥，真实用户 token 一律会被拒绝（DEMO- 测试 token 不受影响）。
                        请任选一种方式提供密钥（必须与 Spring 侧一致）：
                          1) 运行配置里加 VM 参数： -Djwt.secret=你的密钥
                          2) 设置环境变量：        JWT_SECRET=你的密钥
                          3) 确认 src/main/resources/application.yaml 中的 jwt.secret 可直接解析
                        """);
    }
}
