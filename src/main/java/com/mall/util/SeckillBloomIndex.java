package com.mall.util;

import com.mall.common.Constants;
import com.mall.mapper.SeckillActivityMapper;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 秒杀活动 ID 布隆过滤器声明：只提供配置与数据源，机制在 BloomFilterRegistry。
 * 秒杀详情 getSKDetail（防穿透）使用；后台创建活动（4.7.6）落库后须 registry.add(...)，
 * 否则新活动被误杀 404。逻辑删除的活动无法从过滤器移除（布隆特性），会查一次库后 404 兜底。
 *
 * @author 乐乐
 */
@Component
public class SeckillBloomIndex implements BloomFilterSupport {

    private final SeckillActivityMapper seckillActivityMapper;

    public SeckillBloomIndex(SeckillActivityMapper seckillActivityMapper) {
        this.seckillActivityMapper = seckillActivityMapper;
    }

    @Override
    public String filterName() {
        return Constants.BLOOM_FILTER_SECKILL;
    }

    @Override
    public long expectedInsertions() {
        return Constants.BLOOM_SECKILL_EXPECTED_INSERTIONS;
    }

    @Override
    public List<Long> loadAllIds() {
        return seckillActivityMapper.selectAllIds();   // XML SQL：全部未删除活动 ID
    }
}
