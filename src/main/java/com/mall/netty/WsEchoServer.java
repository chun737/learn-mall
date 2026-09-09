package com.mall.netty;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;

public class WsEchoServer {

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
                            // 1 看懂 HTTP 请求（握手阶段是 HTTP 报文）
                            .addLast(new HttpServerCodec())
                            // 2 把拆碎的 HTTP 报文拼成一个完整的（握手请求要整体处理）
                            .addLast(new HttpObjectAggregator(65536))
                            // 3 WebSocket 专属工位：完成 101 握手 + 按帧切消息
                            .addLast(new WebSocketServerProtocolHandler("/ws/chat"))
                            // 4 你的业务（收完整消息）
                            .addLast(new WsEchoHandler());
                      }
                  });

            System.out.println("WebSocket 服务器已启动：端口 9090，路径 /ws/chat");
            server.bind(9090).sync().channel().closeFuture().sync();
        } finally {
            boss.shutdownGracefully();
            worker.shutdownGracefully();
        }
    }

    /** 业务：接 TextWebSocketFrame（一条完整的文本消息） */
    static class WsEchoHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) {
            String msg = frame.text();                       // 取文本
            System.out.println("浏览器说: " + msg);
            ctx.writeAndFlush(new TextWebSocketFrame("服务器收到: " + msg));  // 回话也要装进同款"信封"
        }
    }
}