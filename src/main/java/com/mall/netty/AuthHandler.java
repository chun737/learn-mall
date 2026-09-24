package com.mall.netty;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.QueryStringDecoder;

import java.util.List;

/**
 * 握手阶段认人：从 URL 取 token，验明身份后把自己从流水线摘掉。
 *
 * <p>校验走 {@link StandaloneJwt}（方案 B：独立进程自带 JWT 校验），
 * 不再依赖 Spring 容器注入的 JwtUtil —— 独立进程里那个注入永远不会发生，见 Netty 教程 7.1 / 7.3。
 */
public class AuthHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    /**
     * 本地测试后门：仅显式开启后接受 DEMO-1（用户）、DEMO-9-admin（客服）。
     * 开启方式：VM 参数 -Dnetty.demo.auth=true，或环境变量 NETTY_DEMO_AUTH=true。
     */
    private static final boolean DEMO_ENABLED =
            "true".equalsIgnoreCase(resolveDemoSwitch());

    private static String resolveDemoSwitch() {
        String prop = System.getProperty("netty.demo.auth");
        if (prop != null) {
            return prop.trim();
        }
        String env = System.getenv("NETTY_DEMO_AUTH");
        return (env == null) ? "false" : env.trim();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest req) {
        // 从 URL 里取出 token，例如 /ws/chat?token=xxx → token=xxx
        QueryStringDecoder decoder = new QueryStringDecoder(req.uri());
        List<String> tokenParam = decoder.parameters().get("token");
        String token = (tokenParam == null || tokenParam.isEmpty()) ? "" : tokenParam.get(0);

        Long userId = null;
        String role = null;

        // ---------- ① 本地测试后门（默认关闭，必须显式用 -Dnetty.demo.auth=true 开启）----------
        if (DEMO_ENABLED) {
            // 只允许固定测试账号，避免开启开关后通过 DEMO token 冒充任意用户或管理员。
            if ("DEMO-1".equals(token)) {
                userId = 1L;
                role = "USER";
            } else if ("DEMO-9-admin".equals(token)) {
                userId = 9L;
                role = "ADMIN";
            } else if (token.startsWith("DEMO-")) {
                System.out.println("[Netty] 拒绝未知 DEMO token，允许值为 DEMO-1 或 DEMO-9-admin");
            }
        }

        // ---------- ② 真实 token：独立进程自己验签名 ----------
        if (userId == null) {
            StandaloneJwt.Auth auth = StandaloneJwt.parseUser(token);
            if (auth == null) {
                System.out.println("[Netty] 拒绝连接：token 无效或未登录（remote="
                        + ctx.channel().remoteAddress() + "）");
                ctx.channel().close();               // 没登录 → 直接断开
                return;
            }
            userId = auth.userId();
            role = auth.role();
        }

        // 把"他是谁"贴到这条连接上（给房门贴名字条）
        ctx.channel().attr(ChatKeys.USER_ID).set(userId);
        ctx.channel().attr(ChatKeys.ROLE).set(role);

        // 握手只发生一次，验完身份把自己摘掉：
        // 之后的消息是 WebSocket 帧，再路过我这个"只认 HTTP"的工位会类型不匹配。
        //
        // 顺序说明：先 fireChannelRead 把请求交给下一个工位（WebSocketServerProtocolHandler）
        // 去完成握手，再 remove 自己。反过来写（先摘后传）目前也能跑，但依赖"摘掉后
        // ctx.next 仍指向原节点"这一内部实现细节，不该依赖。
        //
        // retain() 不能省：SimpleChannelInboundHandler 在 channelRead0 返回后会 release 一次，
        // 不 retain 的话下游拿到的是已回收对象，一碰就抛 IllegalReferenceCountException。
        ctx.fireChannelRead(req.retain());
        ctx.pipeline().remove(this);
    }
}
