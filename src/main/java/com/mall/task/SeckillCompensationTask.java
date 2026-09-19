package com.mall.task;

import com.mall.util.SeckillCompensator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 秒杀延迟补偿任务：消费生产端 asyncSend onException 写入的待补偿 ZSET
 * （score = 可补偿时间戳）。到期条目先 DB 复核订单是否存在，再走幂等闸门回补——
 * 15 秒延迟 + DB 准绳，把"发送超时误判"与"消费端建单中"的竞态窗口关死。
 *
 * <p>fixedDelay 防自身重入；处理失败的条目保留在 ZSET 里，下一轮自然重试。
 */
@Component
public class SeckillCompensationTask {

    private static final Logger log = LoggerFactory.getLogger(SeckillCompensationTask.class);

    private final SeckillCompensator seckillCompensator;

    public SeckillCompensationTask(SeckillCompensator seckillCompensator) {
        this.seckillCompensator = seckillCompensator;
    }

    @Scheduled(fixedDelay = 5_000)
    public void drainPendingCompensations() {
        try {
            int handled = seckillCompensator.processPendingCompensations();
            if (handled > 0) {
                log.info("秒杀延迟补偿本轮处理 {} 条", handled);
            }
        } catch (Exception e) {
            log.error("秒杀延迟补偿任务异常", e);
        }
    }
}
