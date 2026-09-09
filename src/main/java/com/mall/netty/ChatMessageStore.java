package com.mall.netty;

/**
 * 阶段一：先打印，保证主流程跑通。
 * 接入 MyBatis 后替换为 mapper.insert(...)，见教程 4.6 阶段二。
 */
public class ChatMessageStore {
    public static void save(Long from, Long to, boolean fromAdmin, String content) {
        System.out.println("[落库] from=" + from + " to=" + to + " fromAdmin=" + fromAdmin + " content=" + content);
        // TODO 阶段二：在这里调用你的 ChatMessageMapper.insert(...)
    }
}
