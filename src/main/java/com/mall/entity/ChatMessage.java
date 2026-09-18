package com.mall.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 客服聊天消息表
 * </p>
 *
 * 设计要点见 Netty 教程 8.3：
 * <ul>
 *   <li>{@code convUserId}：会话归属（这段会话属于哪个用户），让"查整个会话"不必写 OR；</li>
 *   <li>{@code clientMsgId}：客户端消息 ID + 唯一索引，解决重发导致的重复入库；</li>
 *   <li>字段叫 {@code readStatus} 而不是 {@code read} —— read 是 MySQL 保留字。</li>
 * </ul>
 *
 * @author 乐乐
 * @since 2026-09-17
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("chat_message")
public class ChatMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 消息ID（自增，同时也是排序依据：同一秒内的消息靠它保证顺序稳定）
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 会话归属：这条消息属于哪个用户的客服会话（无论谁发的都填用户ID）
     */
    @TableField("conv_user_id")
    private Long convUserId;

    /**
     * 发送人 userId（逻辑外键 -> user.id）
     */
    @TableField("from_id")
    private Long fromId;

    /**
     * 接收人 userId
     */
    @TableField("to_id")
    private Long toId;

    /**
     * 发送方身份：0=用户发的 1=客服发的
     */
    @TableField("from_admin")
    private Integer fromAdmin;

    /**
     * 消息类型：1=文本 2=图片 3=商品卡片 4=系统消息
     */
    @TableField("msg_type")
    private Integer msgType;

    /**
     * 文本内容（非文本类型存摘要即可）
     */
    @TableField("content")
    private String content;

    /**
     * 扩展信息 JSON：图片 URL、商品 ID 等
     */
    @TableField("extra")
    private String extra;

    /**
     * 客户端消息ID：幂等去重，唯一索引 (from_id, client_msg_id)
     */
    @TableField("client_msg_id")
    private String clientMsgId;

    /**
     * 已读状态（接收方视角）：0=未读 1=已读
     */
    @TableField("read_status")
    private Integer readStatus;

    /**
     * 消息状态：0=正常 1=已撤回
     */
    @TableField("status")
    private Integer status;

    /**
     * 发送时间
     */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /**
     * 逻辑删除：0=未删除 1=已删除
     */
    @TableField("deleted")
    private Integer deleted;
}
