package com.mall.common;

import com.mall.enums.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SnowflakeIdGenerator {
    /** 起始时间戳：2020-01-01 00:00:00 */
    private final long START_TIMESTAMP = 1577808000000L;

    /** 序列号占 12 位 */
    private final long SEQUENCE_BITS = 12L;
    /** 机器 ID 占 10 位 */
    private final long WORKER_ID_BITS = 10L;

    /** 时间戳左移位数 = 12 + 10 = 22 */
    private final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;
    /** 机器 ID 左移位数 = 12 */
    private final long WORKER_ID_SHIFT = SEQUENCE_BITS;

    /** 序列号最大值 4095 */
    private final long MAX_SEQUENCE = ~(-1L << SEQUENCE_BITS);

    private final long workerId;
    private long sequence = 0L;
    private long lastTimestamp = -1L;

    public SnowflakeIdGenerator(@Value("${snowflake.worker-id:1}") long workerId) {
        long maxWorkerId = ~(-1L << WORKER_ID_BITS);   // 1023
        if (workerId < 0 || workerId > maxWorkerId) {
            throw new IllegalArgumentException("workerId 必须在 0~1023 之间");
        }
        this.workerId = workerId;
    }

    public synchronized long nextId() {
        long timestamp = System.currentTimeMillis();

        // 时钟回拨：小幅（≤5ms，NTP 校正常见）等待时钟追平；大幅回拨按系统错误抛出并告警。
        // 抛 BusinessException 而非裸 RuntimeException：由全局异常处理器转成规范响应，不再 500。
        if (timestamp < lastTimestamp) {
            long offset = lastTimestamp - timestamp;
            if (offset <= 5) {
                timestamp = waitNextMillis(lastTimestamp);
            } else {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR);
            }
        }

        if (timestamp == lastTimestamp) {
            // 同一毫秒：序列号自增
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0) {
                // 序列号用尽，自旋到下一毫秒
                timestamp = waitNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0;
        }

        lastTimestamp = timestamp;

        // 三段拼接成一个 64 位 ID
        return ((timestamp - START_TIMESTAMP) << TIMESTAMP_SHIFT)
                | (workerId << WORKER_ID_SHIFT)
                | sequence;
    }

    private long waitNextMillis(long lastTimestamp) {
        long timestamp = System.currentTimeMillis();
        while (timestamp <= lastTimestamp) {
            timestamp = System.currentTimeMillis();
        }
        return timestamp;
    }
}
