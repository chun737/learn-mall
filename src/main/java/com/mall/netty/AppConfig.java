package com.mall.netty;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 独立进程的配置读取（Netty 教程 7.3 · 方案 B 的基础设施）。
 *
 * <p>为什么需要它：ChatServer 跑在独立 JVM 里，Spring 不会启动，
 * 所以 {@code @Value("${...}")} 那套注入全部失效。这里自己把
 * {@code application.yaml} 读成扁平的 key-value，供本进程使用。
 *
 * <h3>取值优先级</h3>
 * <ol>
 *   <li>JVM 参数 {@code -Dspring.datasource.url=...}（同名 key）</li>
 *   <li>{@code application.yaml} 里的同名 key</li>
 * </ol>
 * yaml 里写 {@code ${DB_PASSWORD}} 或 {@code ${REDIS_PASSWORD:}} 这种占位符会自动解析：
 * 先查同名环境变量，再查同名 JVM 参数，最后用默认值。
 *
 * <p>支持的 yaml 语法：多级缩进 + {@code key: value} 标量。列表（{@code -} 开头）会被跳过 ——
 * 本进程只关心几个配置项，不值得为它引入 snakeyaml。
 */
public final class AppConfig {

    /** 扁平化后的配置，key 形如 {@code spring.datasource.url} */
    private static final Map<String, String> YAML = loadYaml();

    private AppConfig() {}

    // ======================= 对外 =======================

    /** 取配置：{@code -Dkey=value} 优先，其次 application.yaml */
    public static String get(String key) {
        String fromProp = trimToNull(System.getProperty(key));
        if (fromProp != null) {
            return fromProp;
        }
        return YAML.get(key);
    }

    public static String get(String key, String defaultValue) {
        String value = get(key);
        return (value == null) ? defaultValue : value;
    }

    public static int getInt(String key, int defaultValue) {
        String value = get(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /** 直接读环境变量 */
    public static String env(String name) {
        return trimToNull(System.getenv(name));
    }

    /** 配置文件是否读到内容，供启动横幅判断 */
    public static boolean yamlLoaded() {
        return !YAML.isEmpty();
    }

    // ======================= yaml 加载 =======================

    private static Map<String, String> loadYaml() {
        List<Path> candidates = List.of(
                Path.of("src", "main", "resources", "application.yaml"),
                Path.of("src", "main", "resources", "application.yml"),
                Path.of("application.yaml"),
                Path.of("application.yml"));
        for (Path p : candidates) {
            if (Files.isReadable(p)) {
                try {
                    Map<String, String> parsed = parse(Files.readString(p, StandardCharsets.UTF_8));
                    if (!parsed.isEmpty()) {
                        return parsed;
                    }
                } catch (IOException ignored) {
                    // 读不到就换下一个候选位置
                }
            }
        }
        for (String name : List.of("application.yaml", "application.yml")) {
            try (InputStream in = AppConfig.class.getClassLoader().getResourceAsStream(name)) {
                if (in != null) {
                    Map<String, String> parsed =
                            parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
                    if (!parsed.isEmpty()) {
                        return parsed;
                    }
                }
            } catch (IOException ignored) {
                // 同上
            }
        }
        return Map.of();
    }

    /** 按缩进把 yaml 压成 {@code a.b.c = value} */
    private static Map<String, String> parse(String yaml) {
        Map<String, String> flat = new LinkedHashMap<>();
        List<String> keys = new ArrayList<>();
        List<Integer> indents = new ArrayList<>();

        for (String rawLine : yaml.split("\r?\n")) {
            String line = stripComment(rawLine);
            if (line.isBlank()) {
                continue;
            }
            String trimmed = line.strip();
            if (trimmed.startsWith("-")) {
                continue;                                   // 不支持列表，跳过
            }
            int indent = line.indexOf(trimmed.charAt(0));
            int colon = indexOfKeyColon(trimmed);
            if (colon < 0) {
                continue;
            }
            String key = trimmed.substring(0, colon).trim();
            String value = trimmed.substring(colon + 1).trim();

            // 回到同级或更外层：把路径栈里更深的键弹出去
            while (!indents.isEmpty() && indents.get(indents.size() - 1) >= indent) {
                indents.remove(indents.size() - 1);
                keys.remove(keys.size() - 1);
            }

            if (value.isEmpty()) {
                keys.add(key);                              // 这是个父节点（如 spring:）
                indents.add(indent);
                continue;
            }
            String resolved = resolvePlaceholder(unquote(value));
            if (resolved == null) {
                continue;                                   // 占位符没解析出来，当作"没配"
            }
            StringBuilder path = new StringBuilder();
            for (String k : keys) {
                path.append(k).append('.');
            }
            flat.put(path.append(key).toString(), resolved);
        }
        return flat;
    }

    /**
     * 找 {@code key:} 的冒号。必须"冒号后是空格或行尾"才算，
     * 否则 {@code url: jdbc:mysql://...} 里的 {@code jdbc:} 会被误当成分隔符。
     */
    private static int indexOfKeyColon(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == ':' && (i + 1 == s.length() || s.charAt(i + 1) == ' ')) {
                return i;
            }
        }
        return -1;
    }

    /** 解析 ${ENV} / ${ENV:默认值} */
    private static String resolvePlaceholder(String value) {
        if (value == null || !value.startsWith("${") || !value.endsWith("}")) {
            return value;
        }
        String inner = value.substring(2, value.length() - 1);
        int colon = inner.indexOf(':');
        String name = (colon >= 0) ? inner.substring(0, colon) : inner;
        String defaultValue = (colon >= 0) ? inner.substring(colon + 1) : "";

        String fromEnv = trimToNull(System.getenv(name));
        if (fromEnv != null) {
            return fromEnv;
        }
        String fromProp = trimToNull(System.getProperty(name));
        if (fromProp != null) {
            return fromProp;
        }
        return trimToNull(defaultValue);
    }

    private static String stripComment(String line) {
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) == '#' && (i == 0 || Character.isWhitespace(line.charAt(i - 1)))) {
                return line.substring(0, i);
            }
        }
        return line;
    }

    private static String unquote(String v) {
        if (v.length() >= 2
                && ((v.startsWith("\"") && v.endsWith("\"")) || (v.startsWith("'") && v.endsWith("'")))) {
            return v.substring(1, v.length() - 1);
        }
        return v;
    }

    private static String trimToNull(String v) {
        if (v == null) {
            return null;
        }
        String t = v.trim();
        return t.isEmpty() ? null : t;
    }
}
