package com.mall.consumer;

import com.mall.common.Constants;
import com.mall.dto.OrderCreateDTO;
import com.mall.dto.OrderSkuDTO;
import com.mall.dto.SeckillOrderMessage;
import com.mall.entity.Order;
import com.mall.mapper.OrderMapper;
import com.mall.mapper.ProductSkuMapper;
import com.mall.service.IOrderService;
import com.mall.util.SecurityUtils;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

import static com.mall.common.Constants.*;

@Service
@RocketMQMessageListener(
        topic = "seckill-order-topic",
        consumerGroup = "seckill-consumer-group"
)
public class SeckillOrderConsumer implements RocketMQListener<SeckillOrderMessage> {
    private final IOrderService orderService;
    private final RedisTemplate redisTemplate;

    public SeckillOrderConsumer(IOrderService orderService, RedisTemplate redisTemplate) {
        this.orderService = orderService;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void onMessage(SeckillOrderMessage msg) {
        Long activityId = msg.getActivityId();
        Integer quantity = msg.getQuantity();
        try {
            OrderCreateDTO orderCreateDTO = new OrderCreateDTO();
            orderCreateDTO.setCouponId(msg.getCouponId());
            orderCreateDTO.setAddressId(msg.getAddressId());
            orderCreateDTO.setOrderNo(msg.getOrderNo());
            OrderSkuDTO sku = new OrderSkuDTO();
            sku.setSkuId(msg.getSkuId());
            sku.setQuantity(quantity);
            orderCreateDTO.setSkuList(List.of(sku));
            //2.建单
            orderService.addOrder(orderCreateDTO);
            //写入redis结果
            redisTemplate.opsForHash().put(
                    SECKILL_RESULT_PREFIX + activityId,
                    msg.getUserId().toString(), msg.getOrderNo());
        }
        catch (DuplicateKeyException e) {
            // 订单已存在（重复消息）→ 不处理，幂等跳过（千万别回补！）
        }catch (Exception e) {
            // 真正建单失败：
            Long userId = SecurityUtils.getUserId();
            // 1. 回补库存
            redisTemplate.opsForValue().increment(seckillStockKey(activityId), quantity);
            // 2. 回退已购数量（否则用户被记住"抢过一次"，下次限购拦截）
            redisTemplate.opsForHash().increment(seckillBoughtKey(activityId), userId, -quantity);
            // 3. 标记失败结果（前端 getResult 能查到"下单失败"）
            redisTemplate.opsForHash().put(SECKILL_RESULT_PREFIX + activityId, userId, "FAILED");

        }
    }
}
