package com.mall.netty;

import io.netty.util.AttributeKey;

/** 贴在 Channel 上的"门牌"定义：这条连接是谁、什么角色 */
public final class ChatKeys {
    public static final AttributeKey<Long> USER_ID = AttributeKey.valueOf("userId");
    public static final AttributeKey<String> ROLE  = AttributeKey.valueOf("role");
    private ChatKeys() {}
}