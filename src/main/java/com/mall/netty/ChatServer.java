package com.mall.netty;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.EventExecutorGroup;

/**
 * 客服聊天服务器（独立进程 · 方案 B · 教程 4.7）。
 *
 * <p>为什么是独立进程：聊天服务和主应用解耦，可以单独部署、单独扩容、重启主应用不断开聊天连接。
 * 代价是<b>不能用 Spring 注入</b> —— 所以 JWT 校验走 {@link StandaloneJwt}、
 * 落库走 {@link ChatDb}，两者都自己从 {@link AppConfig} 读配置，见教程 7.1 / 7.3。
 *
 * <p>本地测试：直接 Run main。用 DEMO token（DEMO-1 / DEMO-9-admin）不需要配任何密钥；
 * 想验真实 token，必须让本进程拿到与 Spring 侧一致的 JWT_SECRET。
 *
 * <p>端口可覆盖：VM 参数 {@code -Dnetty.chat.port=9090} 或环境变量 {@code NETTY_CHAT_PORT}。
 *
 * <h3>线程布局</h3>
 * <pre>
 *   boss(1)            接客：只负责 accept
 *   worker(CPU×2)      读写：解析 HTTP/WS 帧、编解码
 *   BIZ_GROUP(16)      业务：ChatHandler 的所有回调（含落库入队）都在这里，不占 IO 线程
 *   chat-msg-writer(1) 落库：批量写 MySQL（ChatMessageStore 内部）
 * </pre>
 */
public class ChatServer {

    /** WebSocket 握手路径，前端必须是 ws://host:port/ws/chat */
    private static final String WS_PATH = "/ws/chat";
    /** 单个 WebSocket 帧上限 64KB，超过会被直接断开 */
    private static final int MAX_FRAME_SIZE = 65536;
    /** 默认端口；注意别和 WsEchoServer（教学示例，9091）搞混 */
    private static final int DEFAULT_PORT = 9090;

    /**
     * 业务线程池（P2）。
     * 落库、拉历史这类"可能变慢"的操作都在这里执行，IO 线程只管收发。
     * 这样哪怕数据库卡 5 秒，其他连接的收发依然正常。
     */
    private static final EventExecutorGroup BIZ_GROUP = new DefaultEventExecutorGroup(16, runnable -> {
        Thread t = new Thread(runnable, "chat-biz");
        t.setDaemon(true);
        return t;
    });

    /** 读写全空闲多少秒判定掉线。前端每 30 秒一次心跳，正常连接不会触发 */
    private static final int IDLE_SECONDS = 90;

    public static void main(String[] args) throws Exception {
        int port = resolvePort();
        initPersistence();

        EventLoopGroup boss = new NioEventLoopGroup(1);   // 接客组：只负责 accept，1 个线程够
        EventLoopGroup worker = new NioEventLoopGroup();  // 干活组：默认 CPU 核数 × 2
        try {
            ServerBootstrap server = new ServerBootstrap();
            server.group(boss, worker)
                  .channel(NioServerSocketChannel.class)
                  .option(ChannelOption.SO_BACKLOG, 1024)                     // 等待 accept 的队列长度
                  .childOption(ChannelOption.SO_KEEPALIVE, true)              // TCP 保活探测
                  .childOption(ChannelOption.TCP_NODELAY, true)               // 关 Nagle：聊天要低延迟
                  .childHandler(new ChannelInitializer<SocketChannel>() {
                      @Override
                      protected void initChannel(SocketChannel ch) {
                          ch.pipeline()
                            .addLast(new HttpServerCodec())                             // 1 HTTP 解析（握手用）
                            .addLast(new HttpObjectAggregator(MAX_FRAME_SIZE))          // 2 HTTP 拼装
                            .addLast(new AuthHandler())                                 // 3 认人（验完自己摘掉）
                            .addLast(new WebSocketServerProtocolHandler(WS_PATH,       // 4 WS 握手 + 帧
                                    null, true, MAX_FRAME_SIZE))
                            .addLast(new IdleStateHandler(0, 0, IDLE_SECONDS))          // 5 空闲检测（读写都算）
                            .addLast(BIZ_GROUP, new ChatHandler());                     // 6 业务（跑在业务线程池）
                      }
                  });

            printBanner(port);

            // 注意：这里只 sync 到 bind 完成，不 sync closeFuture 之外的额外等待 ——
            // 主线程阻塞在 closeFuture 上，直到进程被关掉（Ctrl+C）。
            server.bind(port).sync().channel().closeFuture().sync();
        } finally {
            gracefulShutdown(boss, worker);
        }
    }

    /** 落库初始化：失败不影响聊天，只降级为"打印不落库" */
    private static void initPersistence() {
        ChatDb chatDb = ChatDb.get();
        if (chatDb.init()) {
            ChatMessageStore.init(chatDb);
        } else {
            System.out.println("[Netty] ⚠ 落库未启用（" + ChatDb.status() + "）");
            System.out.println("[Netty]   消息仍可正常收发，但只打印到控制台。");
            System.out.println("[Netty]   要启用请先执行 docx/chat_message.sql 建表，"
                    + "并确认环境变量 DB_PASSWORD 已设置。");
            ChatMessageStore.init(null);
        }
    }

    /** 优雅停机：先停止接客 → 把没落库的消息刷完 → 关连接池 */
    private static void gracefulShutdown(EventLoopGroup boss, EventLoopGroup worker) {
        try {
            ChatMessageStore.flush();               // 队列里剩的消息别丢
        } catch (Exception e) {
            System.out.println("[Netty] 退出前刷盘失败: " + e.getMessage());
        }
        boss.shutdownGracefully();
        worker.shutdownGracefully();
        BIZ_GROUP.shutdownGracefully();
        ChatDb.get().close();
    }

    private static void printBanner(int port) {
        boolean jwtReady = StandaloneJwt.isReady();
        String jwtLine = jwtReady
                ? "已就绪（来源：" + StandaloneJwt.keySource() + "）"
                : "未配置 ⚠ 真实 token 会被拒绝，仅 DEMO- 测试 token 可用";
        String dbLine = ChatDb.isReady()
                ? ChatDb.status()
                : "未启用 ⚠ " + ChatDb.status() + "（消息只打印）";

        System.out.println("""
                =====================================================
                  客服聊天服务器（独立进程 · 方案 B）
                    地址   : ws://localhost:%d%s?token=<JWT>
                    鉴权   : 独立进程自带 JWT 校验（StandaloneJwt）
                    JWT    : %s
                    落库   : %s
                    客户端 : 每 30 秒发 {"type":"ping"} 心跳，%d 秒无来往将被断开
                =====================================================
                """.formatted(port, WS_PATH, jwtLine, dbLine, IDLE_SECONDS));
    }

    /** 端口优先级：VM 参数 netty.chat.port → 环境变量 NETTY_CHAT_PORT → 9090 */
    private static int resolvePort() {
        return AppConfig.getInt("netty.chat.port",
                AppConfig.getInt("NETTY_CHAT_PORT", DEFAULT_PORT));
    }
}
