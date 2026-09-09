package com.mall.netty;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.timeout.IdleStateHandler;

/**
 * 总装：客服聊天服务器（教程 4.7）。
 * 本地测试：直接 Run main，用 DEMO token（见 4.8）——不依赖 Spring 启动。
 * 正式跑：需要 JWT 校验，应让 Spring 应用启动后拉起本类（@PostConstruct / ApplicationRunner）。
 */
public class ChatServer {

    public static void main(String[] args) throws Exception {
        EventLoopGroup boss = new NioEventLoopGroup(1);
        EventLoopGroup worker = new NioEventLoopGroup();
        try {
            ServerBootstrap server = new ServerBootstrap();
            server.group(boss, worker)
                  .channel(NioServerSocketChannel.class)
                  .childHandler(new ChannelInitializer<SocketChannel>() {
                      @Override
                      protected void initChannel(SocketChannel ch) {
                          ch.pipeline()
                            .addLast(new HttpServerCodec())                            // 1 HTTP 解析（握手用）
                            .addLast(new HttpObjectAggregator(65536))                // 2 HTTP 拼装
                            .addLast(new AuthHandler())                             // 3 认人（验完自己摘掉）
                            .addLast(new WebSocketServerProtocolHandler("/ws/chat")) // 4 WS 握手+帧
                            .addLast(new IdleStateHandler(60, 0, 0))                 // 5 心跳：60秒没消息触发事件
                            .addLast(new ChatHandler());                             // 6 业务
                      }
                  });

            System.out.println("客服聊天服务器已启动：端口 9090 路径 /ws/chat");
            server.bind(9090).sync().channel().closeFuture().sync();
        } finally {
            boss.shutdownGracefully();
            worker.shutdownGracefully();
        }
    }
}
