package com.mall.netty;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.util.ReferenceCountUtil;

import java.util.List;

/** 握手阶段认人：从 URL 取 token，验明身份后把自己从流水线摘掉 */
public class AuthHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest req) {
        // 从 URL 里取出 token，例如 /ws/chat?token=xxx → token=xxx
        QueryStringDecoder decoder = new QueryStringDecoder(req.uri());
        List<String> tokenParam = decoder.parameters().get("token");
        String token =    (tokenParam == null || tokenParam.isEmpty()) ? "" : tokenParam.get(0);

        Long userId;
        String role;
        if (token.startsWith("DEMO-")) {          // 本地测试后门，见 4.7；正式环境删掉这段
            // token 形如 DEMO-1（用户）或 DEMO-9-admin（客服）：
            // 先截掉 "DEMO-" 前缀，再取第一个 '-' 之前的数字当 userId
            String raw = token.substring("DEMO-".length());   // "1" 或 "9-admin"
            int dash = raw.indexOf('-');
            String idPart = (dash >= 0) ? raw.substring(0, dash) : raw;
            userId = Long.valueOf(idPart);
            role = token.contains("admin") ? "ADMIN" : "USER";
        } else {
            Object[] user = NettySpringBridge.parseUser(token);   // 调 demo1 的 JWT 校验
            if (user == null) {
                System.out.println("token 无效，拒绝连接");
                ctx.channel().close();               // 没登录 → 直接断开
                return;
            }
            userId = (Long) user[0];
            role = (String) user[1];
        }

        // 把"他是谁"贴到这条连接上（给房门贴名字条）
        ctx.channel().attr(ChatKeys.USER_ID).set(userId);
        ctx.channel().attr(ChatKeys.ROLE).set(role);

        // 握手只发生一次，验完身份把自己摘掉：
        // 之后的消息是 WebSocket 帧，再路过我这个"只认 HTTP"的工位会类型不匹配
        ctx.pipeline().remove(this);
        // 把请求原样传给下一个工位（WebSocketServerProtocolHandler）去完成握手
        ctx.fireChannelRead(ReferenceCountUtil.retain(req));
    }
}