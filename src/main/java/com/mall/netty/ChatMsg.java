package com.mall.netty;

/** 客户端发来的消息：{ "to": 对方userId, "content": "文字" } */
public class ChatMsg {
    private Long to;
    private String content;

    public Long getTo() { return to; }
    public void setTo(Long to) { this.to = to; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}