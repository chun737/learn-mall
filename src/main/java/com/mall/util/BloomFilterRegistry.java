package com.mall.util;

import com.mall.common.Constants;
import jakarta.annotation.PostConstruct;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 布隆过滤器注册中心：机制的唯一实现（防穿透组件的内聚点）。
 *
 * - 启动时（@PostConstruct）收集容器内所有 BloomFilterSupport 实现（Spring 集合注入），
 *   逐个完成 创建句柄 → tryInit 声明容量 → 空表预热 → 入册；
 * - 运行期提供 mightContain/add 的按名转发，业务方只报过滤器名，不持有具体过滤器引用；
 * - 新增业务 = 新写一个 BloomFilterSupport 实现类 + Constants 加名字，本类一行不改（开闭原则）。
 *
 * 失败语义：预热要查库，MySQL 不可用会导致启动失败（与 RedissonConfig 立即建连同哲学：
 * 问题在启动期暴露，不拖到第一个请求）。
 *
 * @author 乐乐
 */
@Component
public class BloomFilterRegistry {

    private final RedissonClient redissonClient;
    /** Spring 自动收集容器中所有 BloomFilterSupport 实现（新增业务自动进入名册） */
    private final List<BloomFilterSupport> supports;
    /** 运行期过滤器目录：name → 初始化完成的实例（创建一次，复用终身） */
    private final Map<String, RBloomFilter<Long>> filters = new ConcurrentHashMap<>();

    public BloomFilterRegistry(RedissonClient redissonClient, List<BloomFilterSupport> supports) {
        this.redissonClient = redissonClient;
        this.supports = supports;
    }

    @PostConstruct
    public void init() {
        for (BloomFilterSupport support : supports) {
            // ① 拿句柄 ② tryInit 声明容量（幂等：已有同名结构返回 false，沿用旧数据，重启不清空预热成果）
            RBloomFilter<Long> filter = redissonClient.getBloomFilter(support.filterName());
            filter.tryInit(support.expectedInsertions(), Constants.BLOOM_FALSE_PROBABILITY);
            // ③ 每次启动全量预热：活动/商品表数据量小，扫表成本可忽略。
            //    不能只按 count()==0 预热——SQL 直插新活动后，布隆里没有新 id，
            //    mightContain 会误判 404，且此时 count>0 导致重启也不重载，必须每次全量重建。
            support.loadAllIds().forEach(filter::add);
            // ④ 入册
            filters.put(support.filterName(), filter);
        }
    }

    /** 查询前拦截：false = 一定不存在，可安全拒绝；true = 可能存在（有误判），继续走缓存/库 */
    public boolean mightContain(String filterName, Long id) {
        return filterOf(filterName).contains(id);
    }

    /** 新增业务落库后必须调用：漏掉会导致新数据被过滤器"一定不存在"误杀（前台查询直接 404） */
    public void add(String filterName, Long id) {
        filterOf(filterName).add(id);
    }
    private void rebuild(String filterName) {
        RLock lock = redissonClient.getLock("lock:bloom:rebuild:" + filterName);
        boolean locked = false;
        try {
            // 只等 200ms：抢不到锁说明别的实例正在重建
            locked = lock.tryLock(200, TimeUnit.MILLISECONDS);
            if (!locked) {
                return;    // 没抢到锁：不影响本次判定，走下面的兜底语义
            }
            BloomFilterSupport support = supportOf(filterName);
            RBloomFilter<Long> filter = filterOf(filterName);
            if (Boolean.FALSE.equals(filter.isExists())) {      // 双重检查：可能别的实例刚重建完
                filter.tryInit(support.expectedInsertions(), Constants.BLOOM_FALSE_PROBABILITY);
                support.loadAllIds().forEach(filter::add);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (locked) {
                lock.unlock();
            }
        }
    }
    private BloomFilterSupport supportOf(String filterName) {
        return supports.stream()
                .filter(s -> s.filterName().equals(filterName))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("未注册的布隆过滤器: " + filterName));
    }
    /** 目录查找：未注册的名字直接明确报错（把拼错名从诡异 NPE 变成可排错的显式异常） */
    private RBloomFilter<Long> filterOf(String filterName) {
        RBloomFilter<Long> filter = filters.get(filterName);
        if (filter == null) {
            throw new IllegalStateException("未注册的布隆过滤器: " + filterName);
        }
        return filter;
    }
}
