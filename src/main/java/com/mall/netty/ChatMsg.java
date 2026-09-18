package com.mall.netty;

import lombok.Data;

/**
 * 客户端发来的消息（统一 JSON 信封，Netty 教程 8.1）。
 *
 * <pre>
 * 聊天：{"type":"chat","to":9,"content":"你好","clientMsgId":"可选的UUID"}
 * 心跳：{"type":"ping"}
 * </pre>
 *
 * 之前只有 {@code to} 和 {@code content} 两个字段；现在多了：
 * <ul>
 *   <li>{@code type}：区分聊天 / 心跳，前端不用再靠"首字符是不是 {"去猜；</li>
 *   <li>{@code clientMsgId}：客户端生成，配合唯一索引做幂等，解决重发导致的重复入库。</li>
 * </ul>
 */
@Data
public class ChatMsg {

    /** 消息类型：chat（默认）=聊天，ping=心跳 */
    private String type;

    /** 对方 userId */
    private Long to;

    /** 文本内容 */
    private String content;

    /** 客户端消息ID，可选；带上就能防重复入库 */
    private String clientMsgId;
}
