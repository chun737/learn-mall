package com.mall.consumer;

import com.mall.common.Constants;
import com.mall.dto.SeckillOrderMessage;
import com.mall.service.IOrderService;
import com.mall.util.SeckillCompensator;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

import static com.mall.common.Constants.*;

/**
 * 秒杀订单消费者：<b>单条消费</b>模式。
 *
 * <h3>为什么是单条而不是 List&lt;SeckillOrderMessage&gt;</h3>
 * 生产端 {@code rocketMQTemplate.asyncSend(topic, msg, callback)} 发的是<b>单个对象</b>
 * （消息体形如 {@code {"activityId":1,...}}），消费端必须声明成同样的单个类型才能反序列化成功。
 *
 * <p>早先这里声明的是 {@code RocketMQListener<List<SeckillOrderMessage>>}，与生产端类型不匹配，
 * 会导致<b>每一次消费都失败</b>。已通过反编译 rocketmq-spring-boot 2.3.6 确认其行为：
 * <ul>
 *   <li>{@code DefaultRocketMQListenerContainer.getMessageType()} 取的是<b>监听器接口的泛型参数</b>，
 *       即 {@code List<SeckillOrderMessage>}；</li>
 *   <li>{@code doConvertMessage()} 调用 {@code SmartMessageConverter.fromMessage(body, List.class, ...)}
 *       把"单条 JSON 对象"按 List 反序列化；而 {@code RocketMQMessageConverter} 只注册了
 *       JavaTimeModule + 关掉 WRITE_DATES_AS_TIMESTAMPS，<b>没有开 ACCEPT_SINGLE_VALUE_AS_ARRAY</b>
 *       → Jackson 把 {@code {} 当数组开头 → MismatchedInputException；</li>
 *   <li>异常被包成 {@code RuntimeException("cannot convert message to " + messageType)}，
 *       由容器捕获后返回 RECONSUME_LATER，重投 {@code maxReconsumeTimes} 次后进死信。</li>
 * </ul>
 *
 * <h3>{@code consumeMessageBatchMaxSize} 不等于"批量消费"</h3>
 * 本容器的并发与顺序监听器都是 {@code for (MessageExt msg : msgs) handleMessage(msg)}——
 * <b>拉到一批后立刻拆开逐条回调</b>，且 {@code handleMessage} 只接收单条。
 * 所以 {@code RocketMQListener<List<T>>} 在这个容器里<b>永远拿不到多条消息</b>，
 * 把它设大只会让"一次 pull 拉几条到内存"，不会把 N 条攒成一个 List 交给监听器。
 *
 * <p>结论：真批量需要绕开本容器（直接用原生 {@code DefaultMQPushConsumer} + 自己接
 * {@code List<MessageExt>} 手动反序列化），收益是摊薄 DB 往返；当前秒杀量级不值得为此
 * 放弃 {@code @RocketMQMessageListener} 提供的重试/死信/线程管理，故保持单条消费。
 */
@Service
@RocketMQMessageListener(
        topic = "seckill-order-topic",
        consumerGroup = "seckill-consumer-group",
        // 1 = 不启用批量预取。消费逻辑本身就是单条，设大只会误导后来者以为存在批量语义
        consumeMessageBatchMaxSize = 1,
        maxReconsumeTimes = 5
)
public class SeckillOrderConsumer implements RocketMQListener<SeckillOrderMessage> {

    private static final Logger log = LoggerFactory.getLogger(SeckillOrderConsumer.class);

    private final IOrderService orderService;
    private final RedisTemplate redisTemplate;
    private final SeckillCompensator seckillCompensator;

    public SeckillOrderConsumer(IOrderService orderService, RedisTemplate redisTemplate,
                                SeckillCompensator seckillCompensator) {
        this.orderService = orderService;
        this.redisTemplate = redisTemplate;
        this.seckillCompensator = seckillCompensator;
    }

    @Override
    public void onMessage(SeckillOrderMessage msg) {
        if (msg == null || msg.getOrderNo() == null) {
            // 空消息没有 orderNo，无法补偿也无法回写结果；直接丢弃并留日志，避免无意义重投
            log.warn("秒杀消费者收到无效消息，已忽略: {}", msg);
            return;
        }
        // 包装成单元素列表复用 Service 的建单方法：那边「失败隔离 + 逐条补偿 + 全批一个事务」
        // 的语义对单条同样正确，无需另写一份。若将来改成真批量，这里换成真实列表即可。
        List<SeckillOrderMessage> msgs = List.of(msg);

        // 非整批性异常都已被 Service 内部隔离成失败集合；这里若抛出（DB 宕机等）
        // 则由容器重投，由 maxReconsumeTimes 封顶后进死信
        Set<String> failedOrderNos = orderService.addSeckillOrderBatch(msgs);

        if (failedOrderNos.contains(msg.getOrderNo())) {
            // 幂等补偿（SETNX 闸门 + DB 复核在 SeckillCompensator 内）：
            // 消息重投 / 死信兜底 / 延迟任务三方撞车也只回补一次
            seckillCompensator.compensateNow(msg);
        } else {
            // 写入 redis 结果（重复消息重复写同值，幂等无害）
            redisTemplate.opsForHash().put(
                    Constants.seckillResultKey(msg.getActivityId()),
                    msg.getUserId().toString(), msg.getOrderNo());
        }
    }
}
