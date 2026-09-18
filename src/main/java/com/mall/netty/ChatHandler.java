package com.mall.netty;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mall.entity.ChatMessage;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.concurrent.GlobalEventExecutor;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 客服聊天业务（Netty 教程 5.3）。
 *
 * <h3>这个类负责四件事</h3>
 * <ol>
 *   <li>握手完成 → 登记路由表 + 补发历史消息（P1 的"上线可见"）</li>
 *   <li>收到消息 → 异步落库 → 按 JSON 信封转发（P5）</li>
 *   <li>心跳 → 回 pong；空闲超时 → 断开（P3）</li>
 *   <li>连接断开 → 从路由表除名（多端支持见 P4）</li>
 * </ol>
 *
 * <h3>两个前提</h3>
 * <ul>
 *   <li>{@code @Sharable}：一个实例服务所有连接，所以内部<b>不能有"属于某条连接"的字段</b>，
 *       状态全部放静态的 ConcurrentHashMap 里。</li>
 *   <li>本 Handler 在独立的业务线程池里执行（ChatServer 里 {@code addLast(bizGroup, ...)}），
 *       所以调用 {@link ChatMessageStore#save} 就算退化成同步写库，也不会卡住 IO 线程。</li>
 * </ul>
 */
@ChannelHandler.Sharable
public class ChatHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {

    /** 忽略前端多传的字段，避免"多了个字段就报格式错误" */
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /** 手动格式化时间：ObjectMapper 默认不认识 LocalDateTime，直接序列化会抛异常 */
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 上线时补发的历史条数 */
    private static final int HISTORY_SIZE = 50;

    /** 路由表：userId → 他的所有在线连接（多标签页/多设备，P4） */
    private static final Map<Long, ChannelGroup> USERS = new ConcurrentHashMap<>();
    private static final Map<Long, ChannelGroup> ADMINS = new ConcurrentHashMap<>();

    // ================= ① 握手完成：登记 + 补发历史 =================

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof WebSocketServerProtocolHandler.HandshakeComplete) {
            onOnline(ctx);
        } else if (evt instanceof IdleStateEvent) {
            // P3：IdleStateHandler 只是"发事件"，必须有人消费它才有意义。
            // 这里选择直接断开：连接已经 90 秒没有任何来往，多半是客户端异常退出。
            // 前端每 30 秒发一次 {"type":"ping"}，正常连接不会被判超时。
            System.out.println("[Netty] 空闲超时，断开连接: " + describe(ctx.channel()));
            ctx.close();
        }
        super.userEventTriggered(ctx, evt);
    }

    private void onOnline(ChannelHandlerContext ctx) {
        Channel ch = ctx.channel();
        Long userId = ch.attr(ChatKeys.USER_ID).get();
        String role = ch.attr(ChatKeys.ROLE).get();
        if (userId == null) {
            return;                                  // 没认过人（异常情况），忽略
        }

        boolean isAdmin = "ADMIN".equals(role);
        Map<Long, ChannelGroup> table = isAdmin ? ADMINS : USERS;
        table.computeIfAbsent(userId, id -> new DefaultChannelGroup(
                (isAdmin ? "admin-" : "user-") + id, GlobalEventExecutor.INSTANCE)).add(ch);

        System.out.println("上线: userId=" + userId + " role=" + role
                + "（在线用户 " + USERS.size() + "，在线客服 " + ADMINS.size() + "）");

        sendHistory(ctx, userId, isAdmin);
    }

    /** 补发离线期间的消息 —— 这才让"消息已保存，上线可见"名副其实 */
    private void sendHistory(ChannelHandlerContext ctx, Long userId, boolean isAdmin) {
        if (!ChatDb.isReady()) {
            return;
        }
        try {
            // 客服：拉所有未读（可能是多个用户发来的）；用户：拉自己这段会话的最近 N 条
            List<ChatMessage> history = isAdmin
                    ? ChatDb.get().selectUnread(userId)
                    : ChatDb.get().selectRecent(userId, 0L, HISTORY_SIZE);
            if (history.isEmpty()) {
                return;
            }
            if (!isAdmin) {
                Collections.reverse(history);        // 查询是倒序，发给前端要按时间正序
            }

            List<Map<String, Object>> list = new ArrayList<>(history.size());
            for (ChatMessage m : history) {
                list.add(toEnvelope(m));
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("type", "history");
            payload.put("list", list);
            ctx.writeAndFlush(new TextWebSocketFrame(writeJson(payload)));
            System.out.println("补发历史: userId=" + userId + " 共 " + list.size() + " 条");
        } catch (Exception e) {
            System.out.println("[Netty] 拉取历史消息失败（不影响聊天）: " + e.getMessage());
        }
    }

    // ================= ② 收到消息：落库 + 转发 =================

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) {
        Long myId = ctx.channel().attr(ChatKeys.USER_ID).get();
        String role = ctx.channel().attr(ChatKeys.ROLE).get();
        boolean iAmAdmin = "ADMIN".equals(role);
        if (myId == null) {
            sendError(ctx, "连接未完成鉴权");
            return;
        }

        ChatMsg msg;
        try {
            msg = MAPPER.readValue(frame.text(), ChatMsg.class);
        } catch (Exception e) {
            sendError(ctx, "格式错误，应发 {\"type\":\"chat\",\"to\":数字,\"content\":\"文字\"}");
            return;
        }

        // 心跳：只回一个 pong，不落库、不转发（P3）
        if ("ping".equalsIgnoreCase(msg.getType())) {
            Map<String, Object> pong = new LinkedHashMap<>();
            pong.put("type", "pong");
            ctx.writeAndFlush(new TextWebSocketFrame(writeJson(pong)));
            return;
        }

        if (msg.getTo() == null || msg.getContent() == null || msg.getContent().isBlank()) {
            sendError(ctx, "缺少 to 或 content");
            return;
        }

        // 2.1 先落库（铁律：内存随时会断，库是唯一可靠存储）
        // 这里是"入队"，真正的写库在 ChatMessageStore 的独立线程里攒批完成，不阻塞 IO。
        ChatMessageStore.save(new ChatMessage()
                .setConvUserId(iAmAdmin ? msg.getTo() : myId)   // 会话归属永远是"用户"
                .setFromId(myId)
                .setToId(msg.getTo())
                .setFromAdmin(iAmAdmin ? 1 : 0)
                .setMsgType(1)
                .setContent(msg.getContent())
                .setClientMsgId(msg.getClientMsgId())
                .setReadStatus(0)
                .setStatus(0));

        // 2.2 再转发：统一 JSON 信封，前端按 type 分发，不用猜（P5）
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("type", "chat");
        out.put("from", myId);
        out.put("to", msg.getTo());
        out.put("fromAdmin", iAmAdmin);
        out.put("content", msg.getContent());
        if (msg.getClientMsgId() != null && !msg.getClientMsgId().isBlank()) {
            out.put("clientMsgId", msg.getClientMsgId());
        }
        out.put("time", LocalDateTime.now().format(TIME_FMT));

        ChannelGroup targets = (iAmAdmin ? USERS : ADMINS).get(msg.getTo());
        if (targets != null && !targets.isEmpty()) {
            targets.writeAndFlush(new TextWebSocketFrame(writeJson(out)));
        } else {
            // 对方不在线：消息已在库里，等他上线时由 sendHistory 补发
            sendSystem(ctx, "对方不在线，消息已保存，上线可见");
        }
    }

    // ================= ③ 连接断开：从路由表除名 =================

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        Channel ch = ctx.channel();
        Long userId = ch.attr(ChatKeys.USER_ID).get();
        if (userId != null) {
            removeFrom(USERS, userId, ch);
            removeFrom(ADMINS, userId, ch);
            System.out.println("下线: userId=" + userId
                    + "（在线用户 " + USERS.size() + "，在线客服 " + ADMINS.size() + "）");
        }
        super.channelInactive(ctx);          // P9：继续传播事件，别把它掐断
    }

    /**
     * 从路由表移除一条连接。
     *
     * <p>用 {@code computeIfPresent} 而不是"先 get 判断再 remove"：
     * 后者在"最后一个连接刚断开、新连接同时进来"时存在竞态，可能把新连接误删
     * （用户会表现为"假下线"）。ConcurrentHashMap 的 compute 系列对同一个 key 是原子的。
     */
    private static void removeFrom(Map<Long, ChannelGroup> table, Long userId, Channel ch) {
        table.computeIfPresent(userId, (id, group) -> {
            group.remove(ch);
            return group.isEmpty() ? null : group;
        });
    }

    // ================= ④ 异常兜底 =================

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        // 不处理的话异常会一路传到流水线末端，只留一行 WARN，排查时毫无线索
        System.out.println("[Netty] ✗ 连接异常 " + describe(ctx.channel()) + " : "
                + cause.getClass().getSimpleName() + " - " + cause.getMessage());
        ctx.close();
    }

    // ================= 工具 =================

    private static void sendError(ChannelHandlerContext ctx, String content) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "error");
        m.put("content", content);
        ctx.writeAndFlush(new TextWebSocketFrame(writeJson(m)));
    }

    private static void sendSystem(ChannelHandlerContext ctx, String content) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "system");
        m.put("content", content);
        ctx.writeAndFlush(new TextWebSocketFrame(writeJson(m)));
    }

    /** 把库里的消息转成前端认识的信封（时间手动格式化，见 TIME_FMT 注释） */
    private static Map<String, Object> toEnvelope(ChatMessage m) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", "chat");
        map.put("from", m.getFromId());
        map.put("to", m.getToId());
        map.put("fromAdmin", m.getFromAdmin() != null && m.getFromAdmin() == 1);
        map.put("content", m.getContent());
        map.put("time", m.getCreatedAt() == null ? "" : m.getCreatedAt().format(TIME_FMT));
        if (m.getClientMsgId() != null) {
            map.put("clientMsgId", m.getClientMsgId());
        }
        return map;
    }

    private static String writeJson(Map<String, Object> payload) {
        try {
            return MAPPER.writeValueAsString(payload);
        } catch (Exception e) {
            return "{\"type\":\"error\",\"content\":\"消息序列化失败\"}";
        }
    }

    private static String describe(Channel ch) {
        Long userId = ch.attr(ChatKeys.USER_ID).get();
        return ch.remoteAddress() + (userId == null ? "" : " userId=" + userId);
    }
}
