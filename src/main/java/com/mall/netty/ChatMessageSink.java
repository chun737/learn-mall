package com.mall.netty;

import com.mall.entity.ChatMessage;

import java.util.List;

/**
 * 消息落地目标（Netty 教程 8.3）。
 *
 * <p>把"攒批"和"落到哪"分开：
 * <ul>
 *   <li>{@link ChatMessageStore} 只负责攒批（收一条存一条太费数据库往返）；</li>
 *   <li>本接口的实现在决定"写进 MySQL 还是发到 MQ"。</li>
 * </ul>
 *
 * <p>当前实现：{@link ChatDb}（独立进程直连 MySQL）。
 * 以后要换成 RocketMQ，只需要再写一个实现类，业务代码一行不用改。
 */
@FunctionalInterface
public interface ChatMessageSink {

    /**
     * 批量写入。实现方需要保证"要么都成功、要么抛异常"，
     * 抛出的异常由 {@link ChatMessageStore} 统一记录，不会影响 Netty 的收发。
     */
    void write(List<ChatMessage> batch);
}
