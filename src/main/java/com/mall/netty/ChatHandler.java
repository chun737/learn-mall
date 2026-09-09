package com.mall.netty;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ChannelHandler.Sharable    // 一个实例被所有连接共享，所以它内部不存"某个连接"的状态
public class ChatHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // 路由表：userId → 连接。相当于酒店前台的"在住客人名单"
    private static final Map<Long, Channel> USERS  = new ConcurrentHashMap<>();
    private static final Map<Long, Channel> ADMINS = new ConcurrentHashMap<>();

    // ================= ① 握手完成事件：登记路由表 =================
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof WebSocketServerProtocolHandler.HandshakeComplete) {
            Long userId = ctx.channel().attr(ChatKeys.USER_ID).get();
            String role = ctx.channel().attr(ChatKeys.ROLE).get();
            if (userId == null) return;                       // 没认过人（异常情况），忽略

            if ("ADMIN".equals(role)) ADMINS.put(userId, ctx.channel());
            else                      USERS.put(userId, ctx.channel());
            System.out.println("上线: userId=" + userId + " role=" + role
                    + "（在线用户 " + USERS.size() + "，在线客服 " + ADMINS.size() + "）");
        }
        super.userEventTriggered(ctx, evt);
    }

    // ================= ② 收到消息：落库 → 转发 =================
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) throws Exception {
        String json = frame.text();
        System.out.println("收到: " + json);

        // 2.1 解析 JSON
        ChatMsg msg;
        try {
            msg = MAPPER.readValue(json, ChatMsg.class);
        } catch (Exception e) {
            ctx.writeAndFlush(new TextWebSocketFrame("{\"error\":\"格式错误，应发 {\\\"to\\\":数字,\\\"content\\\":\\\"文字\\\"}\"}"));
            return;
        }

        Long myId   = ctx.channel().attr(ChatKeys.USER_ID).get();
        String role = ctx.channel().attr(ChatKeys.ROLE).get();
        boolean iAmAdmin = "ADMIN".equals(role);

        // 2.2 先落库（聊天铁律：先存再发。内存随时会断，库是唯一可靠存储）
        ChatMessageStore.save(myId, msg.getTo(), iAmAdmin, msg.getContent());

        // 2.3 转发：用户 → 找客服表；客服 → 找用户表
        Channel target = iAmAdmin ? USERS.get(msg.getTo()) : ADMINS.get(msg.getTo());
        if (target != null && target.isActive()) {
            target.writeAndFlush(new TextWebSocketFrame(msg.getContent()));
        } else {
            // 对方不在线：消息已在库里，等他上线后按 4.6 的 SQL 拉走
            ctx.writeAndFlush(new TextWebSocketFrame("{\"info\":\"对方不在线，消息已保存，上线可见\"}"));
        }
    }

    // ================= ③ 连接断开：从路由表除名 =================
    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        Long userId = ctx.channel().attr(ChatKeys.USER_ID).get();
        if (userId != null) {
            // 两参 remove(key, value)：只有"这 userId 名下的确是这条连接"才删。
            // 防止：断网重连后，旧连接的清理误删掉新连接
            USERS.remove(userId, ctx.channel());
            ADMINS.remove(userId, ctx.channel());
            System.out.println("下线: userId=" + userId);
        }
    }
}