package com.mall.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mall.dto.SeckillOrderMessage;
import com.mall.util.SeckillCompensator;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

/**
 * 秒杀死信消费者（审计 高-1）：消息重投 {@code maxReconsumeTimes} 次仍失败后进入
 * {@code %DLQ%seckill-consumer-group}，此前无人消费 = Redis 预扣的库存/限购额度永不回补，
 * 活动被"幽灵"扣空（少卖）。
 *
 * <p>每条死信解析回 {@link SeckillOrderMessage} 交给 {@link SeckillCompensator}：
 * 幂等闸门 + DB 复核保证与消费端失败兜底、延迟补偿任务三方撞车也只补一次；
 * 若死信对应的订单其实已建成（如某次重投实际成功但 ack 丢失），DB 复核会拦下回补。
 */
@Service
@RocketMQMessageListener(
        topic = "%DLQ%seckill-consumer-group",
        consumerGroup = "seckill-dlq-consumer-group",
        // 死信处理失败重投 2 次封顶：解析失败的消息永远解析不出来，重试无意义；
        // Redis 抖动场景下闸门已释放，重投后可继续补偿
        maxReconsumeTimes = 2
)
public class SeckillDlqConsumer implements RocketMQListener<MessageExt> {

    private static final Logger log = LoggerFactory.getLogger(SeckillDlqConsumer.class);

    private final SeckillCompensator seckillCompensator;
    private final ObjectMapper objectMapper;

    public SeckillDlqConsumer(SeckillCompensator seckillCompensator, ObjectMapper objectMapper) {
        this.seckillCompensator = seckillCompensator;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onMessage(MessageExt message) {
        SeckillOrderMessage msg;
        try {
            String body = new String(message.getBody(), StandardCharsets.UTF_8);
            msg = objectMapper.readValue(body, SeckillOrderMessage.class);
        } catch (Exception e) {
            // 解析失败的消息永远无法补偿，重试无意义：记日志后确认消费（丢弃）
            log.error("死信消息无法解析为秒杀订单，已丢弃: msgId={}", message.getMsgId(), e);
            return;
        }
        if (msg.getOrderNo() == null || msg.getActivityId() == null
                || msg.getUserId() == null || msg.getQuantity() == null) {
            log.error("死信消息关键字段缺失，无法补偿，已丢弃: msgId={}, orderNo={}",
                    message.getMsgId(), msg.getOrderNo());
            return;
        }
        // Redis 回补失败时抛出让容器重投（闸门已在 Compensator 内释放，重投后可重试）
        seckillCompensator.compensateNow(msg);
    }
}
