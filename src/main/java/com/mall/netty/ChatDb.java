package com.mall.netty;

import com.mall.entity.ChatMessage;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 独立进程的数据库通道（Netty 教程 7.3 方案 B + 8.3 落库）。
 *
 * <p>为什么不用 Spring 那边的 MyBatis Mapper：9090 是独立 JVM，没有 Spring 容器，
 * Mapper 根本不会被创建。所以这里自己建一个小连接池 + 手写 SQL ——
 * 聊天只需要"批量插入 + 三个查询"，用 JDBC 反而最省事、零配置坑。
 *
 * <p>配置来源见 {@link AppConfig}：默认读 {@code application.yaml} 的
 * {@code spring.datasource.*}，密码占位符 {@code ${DB_PASSWORD}} 会自动解析环境变量。
 * 也可以用 {@code -Dspring.datasource.url=...} 临时覆盖。
 *
 * <p>连不上数据库不会导致聊天服务起不来：{@link #init()} 返回 false，
 * 上层降级为"只打印不落库"，保证本地没配数据库时也能调试。
 */
public final class ChatDb implements ChatMessageSink {

    private static final ChatDb INSTANCE = new ChatDb();

    private static final String SQL_INSERT = """
            INSERT INTO chat_message
              (conv_user_id, from_id, to_id, from_admin, msg_type, content, extra,
               client_msg_id, read_status, status, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, 0, NOW())
            ON DUPLICATE KEY UPDATE id = id
            """;

    private static final String SQL_RECENT_FIRST = """
            SELECT * FROM chat_message
             WHERE conv_user_id = ? AND deleted = 0
             ORDER BY id DESC LIMIT ?
            """;

    private static final String SQL_RECENT_BEFORE = """
            SELECT * FROM chat_message
             WHERE conv_user_id = ? AND deleted = 0 AND id < ?
             ORDER BY id DESC LIMIT ?
            """;

    private static final String SQL_UNREAD = """
            SELECT * FROM chat_message
             WHERE to_id = ? AND read_status = 0 AND deleted = 0
             ORDER BY id ASC LIMIT 500
            """;

    private static final String SQL_MARK_READ = """
            UPDATE chat_message SET read_status = 1
             WHERE to_id = ? AND from_id = ? AND read_status = 0
            """;

    private volatile HikariDataSource dataSource;
    private volatile String status = "未初始化";

    private ChatDb() {}

    public static ChatDb get() {
        return INSTANCE;
    }

    /** 落库是否可用 */
    public static boolean isReady() {
        return INSTANCE.dataSource != null;
    }

    /** 落库状态描述，供启动横幅显示 */
    public static String status() {
        return INSTANCE.status;
    }

    // ======================= 生命周期 =======================

    /**
     * 建连接池并试连一次。
     *
     * @return true=落库可用；false=配置缺失或连不上（此时聊天仍能跑，只是消息不落库）
     */
    public boolean init() {
        String url = AppConfig.get("spring.datasource.url");
        if (url == null || url.isBlank()) {
            status = "未启用（读不到 spring.datasource.url）";
            return false;
        }
        String username = AppConfig.get("spring.datasource.username", "root");
        String password = AppConfig.get("spring.datasource.password", "");

        try {
            HikariConfig cfg = new HikariConfig();
            cfg.setJdbcUrl(url);
            cfg.setUsername(username);
            cfg.setPassword(password);
            cfg.setPoolName("chat-db");
            cfg.setMaximumPoolSize(8);          // 落库是攒批写入，不需要大池子
            cfg.setMinimumIdle(2);
            cfg.setConnectionTimeout(3000);
            dataSource = new HikariDataSource(cfg);

            try (Connection conn = dataSource.getConnection()) {
                conn.isValid(2);
            }
            status = "已连接 " + brief(url);
            return true;
        } catch (Exception e) {
            status = "连接失败：" + e.getClass().getSimpleName() + " - " + e.getMessage();
            close();
            return false;
        }
    }

    public void close() {
        HikariDataSource ds = dataSource;
        dataSource = null;
        if (ds != null) {
            try {
                ds.close();
            } catch (Exception ignored) {
                // 关闭失败无需处理
            }
        }
    }

    // ======================= 写入 =======================

    @Override
    public void write(List<ChatMessage> batch) {
        HikariDataSource ds = dataSource;
        if (ds == null || batch == null || batch.isEmpty()) {
            return;
        }
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_INSERT)) {
            for (ChatMessage m : batch) {
                ps.setLong(1, m.getConvUserId());
                ps.setLong(2, m.getFromId());
                ps.setLong(3, m.getToId());
                ps.setInt(4, m.getFromAdmin() == null ? 0 : m.getFromAdmin());
                ps.setInt(5, m.getMsgType() == null ? 1 : m.getMsgType());
                ps.setString(6, m.getContent() == null ? "" : m.getContent());
                ps.setString(7, m.getExtra());
                ps.setString(8, m.getClientMsgId());
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException e) {
            throw new RuntimeException("批量写入 chat_message 失败（表建了吗？见 docx/chat_message.sql）: "
                    + e.getMessage(), e);
        }
    }

    // ======================= 查询 =======================

    /** 拉一段会话的最近 N 条（倒序返回）。beforeId<=0 表示从最新开始 */
    public List<ChatMessage> selectRecent(Long convUserId, long beforeId, int size) {
        HikariDataSource ds = dataSource;
        if (ds == null) {
            return List.of();
        }
        boolean useCursor = beforeId > 0;
        String sql = useCursor ? SQL_RECENT_BEFORE : SQL_RECENT_FIRST;
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, convUserId);
            if (useCursor) {
                ps.setLong(2, beforeId);
                ps.setInt(3, size);
            } else {
                ps.setInt(2, size);
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<ChatMessage> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(map(rs));
                }
                return list;
            }
        } catch (SQLException e) {
            throw new RuntimeException("查询 chat_message 历史失败: " + e.getMessage(), e);
        }
    }

    /** 拉某人所有未读消息 */
    public List<ChatMessage> selectUnread(Long toId) {
        HikariDataSource ds = dataSource;
        if (ds == null) {
            return List.of();
        }
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_UNREAD)) {
            ps.setLong(1, toId);
            try (ResultSet rs = ps.executeQuery()) {
                List<ChatMessage> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(map(rs));
                }
                return list;
            }
        } catch (SQLException e) {
            throw new RuntimeException("查询未读消息失败: " + e.getMessage(), e);
        }
    }

    /** 把 fromId 发给 toId 的未读消息全部标记为已读 */
    public int markRead(Long toId, Long fromId) {
        HikariDataSource ds = dataSource;
        if (ds == null) {
            return 0;
        }
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_MARK_READ)) {
            ps.setLong(1, toId);
            ps.setLong(2, fromId);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("标记已读失败: " + e.getMessage(), e);
        }
    }

    // ======================= 内部 =======================

    private static ChatMessage map(ResultSet rs) throws SQLException {
        return new ChatMessage()
                .setId(rs.getLong("id"))
                .setConvUserId(rs.getLong("conv_user_id"))
                .setFromId(rs.getLong("from_id"))
                .setToId(rs.getLong("to_id"))
                .setFromAdmin(rs.getInt("from_admin"))
                .setMsgType(rs.getInt("msg_type"))
                .setContent(rs.getString("content"))
                .setExtra(rs.getString("extra"))
                .setClientMsgId(rs.getString("client_msg_id"))
                .setReadStatus(rs.getInt("read_status"))
                .setStatus(rs.getInt("status"))
                .setCreatedAt(toLocalDateTime(rs.getTimestamp("created_at")))
                .setDeleted(rs.getInt("deleted"));
    }

    private static LocalDateTime toLocalDateTime(Timestamp ts) {
        return (ts == null) ? null : ts.toLocalDateTime();
    }

    /** 去掉 url 后面的参数，日志里看着清爽 */
    private static String brief(String url) {
        int q = url.indexOf('?');
        return (q > 0) ? url.substring(0, q) : url;
    }
}
