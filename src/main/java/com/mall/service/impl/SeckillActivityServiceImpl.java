package com.mall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mall.common.BusinessException;
import com.mall.common.Constants;
import com.mall.common.SnowflakeIdGenerator;
import com.mall.dto.OrderCreateDTO;
import com.mall.dto.OrderSkuDTO;
import com.mall.dto.SeckillCreateDTO;
import com.mall.dto.SeckillOrderMessage;
import com.mall.entity.Order;
import com.mall.entity.ProductSku;
import com.mall.entity.SeckillActivity;
import com.mall.mapper.ProductSkuMapper;
import com.mall.mapper.SeckillActivityMapper;
import com.mall.service.IOrderService;
import com.mall.service.IProductSkuService;
import com.mall.service.ISeckillActivityService;
import com.mall.util.BloomFilterRegistry;
import com.mall.util.SeckillCompensator;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mall.util.SecurityUtils;
import com.mall.vo.*;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static com.mall.enums.ErrorCode.*;

/**
 * <p>
 * 秒杀活动表 服务实现类
 * </p>
 * 活动列表设计（frontend-api-guide 3.10）：
 * - 读多写少、数据量小（几十条）→ 整表缓存 60 秒 + Java 内存过滤分页，而非按页缓存
 *   （按页缓存在新增/删除活动时页内容整体漂移，会翻页重复/漏数据，是分页缓存反模式）；
 * - 活动状态由时间窗口（start/end 与 now 比较）实时计算，不用 DB 的 status 字段
 *   （没有定时任务流转它，读它必然失真）；
 * - availableStock 不走缓存（缓存值滞后），每条实时读 Redis 预扣计数，DB 值兜底。
 *   两处库存的分工：Redis 是"预扣"（Lua 原子扣减，抢购准入的唯一权威）；
 *   DB 的 available_stock 由 MQ 消费者在建单成功后异步扣减（OrderServiceImpl.addSeckillOrderBatch），
 *   并在取消/超时/退款时由 SeckillStockRestorer 同步回补。
 *   两者差值 = 已预扣但尚未建单的部分（消费有秒级延迟），后台对账页明显偏离即说明消费者积压或回补异常。
 *
 * 数据库访问走 SeckillActivityMapper.xml 的 SQL（项目规范：CRUD 只用 SQL 语句）。
 *
 * @author 乐乐
 * @since 2026-08-27
 */
@Service
public class SeckillActivityServiceImpl extends ServiceImpl<SeckillActivityMapper, SeckillActivity> implements ISeckillActivityService {

    private static final Logger log = LoggerFactory.getLogger(SeckillActivityServiceImpl.class);

    private final SeckillActivityMapper seckillActivityMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final BloomFilterRegistry bloomFilterRegistry;
    private final IOrderService orderService;
    private final ProductSkuMapper productSkuMapper;
    private final SnowflakeIdGenerator snowflakeIdGenerator;
    /** 秒杀原子预扣脚本：KEYS[库存key, 已购集合key, 结果key]，ARGV[userId, 数量, 限购] */
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    // 活动列表/详情的缓存 key 与 TTL 定义在 Constants（SECKILL_LIST_KEY / TTL_SECKILL_LIST / SECKILL_DETAIL_KEY_PREFIX / TTL_SECKILL_DETAIL / MAX_PAGE_SIZE）
    // 布隆过滤器机制在 BloomFilterRegistry（本业务声明见 SeckillBloomIndex）
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("Seckill.lua"));  // 脚本来源
        SECKILL_SCRIPT.setResultType(Long.class);                                // 返回类型（对应转换表的 integer）
    }

    private final RocketMQTemplate rocketMQTemplate;
    private final SeckillCompensator seckillCompensator;

    public SeckillActivityServiceImpl(SeckillActivityMapper seckillActivityMapper,
                                      RedisTemplate<String, Object> redisTemplate,
                                      BloomFilterRegistry bloomFilterRegistry,
                                      IOrderService orderService,
                                      ProductSkuMapper productSkuMapper,
                                      SnowflakeIdGenerator snowflakeIdGenerator, RocketMQTemplate rocketMQTemplate,
                                      SeckillCompensator seckillCompensator) {
        this.seckillActivityMapper = seckillActivityMapper;
        this.redisTemplate = redisTemplate;
        this.bloomFilterRegistry = bloomFilterRegistry;
        this.orderService = orderService;
        this.productSkuMapper = productSkuMapper;
        this.snowflakeIdGenerator = snowflakeIdGenerator;
        this.rocketMQTemplate = rocketMQTemplate;
        this.seckillCompensator = seckillCompensator;
    }

    @Override
    public PageResult<SeckillActivityVO> getSkLists(Integer status, Integer pageNum, Integer pageSize) {
        // 0. 参数规整与校验：status 只接受 0/1/2/null（不传=全部）；页码从 1 起，pageSize 上限 100 防恶意大页
        if (status != null && status != 0 && status != 1 && status != 2) {
            throw new BusinessException(PARAM_ERROR);
        }
        int page = (pageNum == null || pageNum < 1) ? 1 : pageNum;
        int size = (pageSize == null || pageSize < 1) ? 10 : Math.min(pageSize, Constants.MAX_PAGE_SIZE);

        // 1. 全量加载（缓存命中或 SQL 回源）——缓存不区分 status，过滤统一在内存做（见类注释）
        List<SeckillActivityVO> all = loadAll();
        LocalDateTime now = LocalDateTime.now();

        // 2. 实时计算状态并过滤：DB 不维护实时状态，每次请求按时间窗口现算（3.6.5 activityStatus 语义）
        //    decorate 顺带完成 VO 装饰：statusText 供前端直显，serverTime 供前端对齐倒计时（避免客户端时钟偏差）
        List<SeckillActivityVO> filtered = new ArrayList<>(all.size());
        for (SeckillActivityVO vo : all) {
            int current = decorate(vo, now);
            if (status == null || status == current) {
                filtered.add(vo);
            }
        }
        // 注：SQL 已 ORDER BY start_time DESC，过滤保序，无需二次排序

        // 3. 内存分页：offset 换算为 [from, to) 闭开区间，越界钳制成空页而非报错
        //    （第 100 页只有 12 条数据时返回空列表 + 真实 total，前端据此停翻页）
        long total = filtered.size();
        int from = Math.min((page - 1) * size, filtered.size());
        int to = (int) Math.min(from + size, filtered.size());
        List<SeckillActivityVO> pageList = new ArrayList<>(filtered.subList(from, to));

        // 4. 库存装饰：只装饰当前页（省 N 次 Redis 读）。读 Redis 预扣计数，key 不存在/Redis 故障回落 DB 值
        pageList.forEach(this::overlayLiveStock);

        return PageResult.of(pageList, total, page, size);
    }

    @Override
    @Transactional
    public SeckillActivityVO getSKDetail(Long id) {
        // 1. 读缓存：key = 详情前缀 + 真实 id；Redis 故障（抛异常）静默降级，null（key 不存在）继续走查库
        String key = Constants.SECKILL_DETAIL_KEY_PREFIX + id;
        try {
            Object cached = redisTemplate.opsForValue().get(key);
            if(cached instanceof SeckillActivityVO seckillActivityVO){
                decorate(seckillActivityVO, LocalDateTime.now());
                return seckillActivityVO;
            }
        } catch (Exception ignored) {
        }
        // 2. 布隆拦截：一定不存在的活动直接 404，零 DB 成本（防穿透；位于缓存未命中之后、查库之前）。
        //    已删除的活动仍会通过（布隆只加不删），由下方查库 null → 404 兜底
        if (!bloomFilterRegistry.mightContain(Constants.BLOOM_FILTER_SECKILL, id)) {
            throw new BusinessException(NOT_FOUND);
        }
        // 3. 未命中：SQL 按 id 查（XML 已有 sa.id = #{id} 过滤）
        SeckillActivityVO vo = seckillActivityMapper.selectDetailById(id);
        if (vo == null) {
            throw new BusinessException(NOT_FOUND);        // 404，不是 PARAM_ERROR
        }
        // 3. 回填缓存：必须带 TTL，否则活动状态流转/被终止后详情永不更新
        try {
            redisTemplate.opsForValue().set(key, vo, Constants.TTL_SECKILL_DETAIL);
        } catch (Exception ignored) { }
        decorate(vo, LocalDateTime.now());
        return vo;
    }

    @Override
    public SeckillResultVO seckill(Long id, Long addressId, Integer quantity, Long couponId) {
        // 0. 秒杀订单不支持用券（产品规则）：在 Lua 预扣之前 fail fast。
        //    修复前 couponId 被静默忽略——用户选了券却无折扣无核销（高-4 的表现之一）
        if (couponId != null) {
            throw new BusinessException(SECKILL_COUPON_NOT_SUPPORTED);
        }
        // 1. 活动信息（不存在的 id 在此 404；布隆拦截绝对不存在的 id）
        SeckillActivityVO activity = getSKDetail(id);
        // 2. 终止状态检查：DB 的 status 只由终止接口写 2，是运营意志，优先于时间窗推算
        //    （自然结束的活动 status 无人流转仍为 0，不会误伤，由下方时间窗检查报 42102）
        SeckillActivity dbActivity = seckillActivityMapper.selectById(id);
        if (dbActivity == null || dbActivity.getDeleted() == Constants.DELETED) {
            throw new BusinessException(NOT_FOUND);
        }
        if (dbActivity.getStatus() != null
                && dbActivity.getStatus() == Constants.SECKILL_STATUS_ENDED) {
            throw new BusinessException(SECKILL_ENDED);      // 被终止：报"已结束"而非"售罄"
        }
        // 3. 时间窗状态检查（正常流转口径）
        if (activity.getActivityStatus() == Constants.SECKILL_STATUS_NOT_STARTED) {
            throw new BusinessException(SECKILL_NOT_STARTED);    // 42101
        }
        if (activity.getActivityStatus() == Constants.SECKILL_STATUS_ENDED) {
            throw new BusinessException(SECKILL_ENDED);          // 42102
        }
        Long userId = SecurityUtils.getUserId();
        // KEYS[1] 必须是库存 key（脚本对它 GET/DECRBY），传详情缓存 key 会把缓存当库存扣坏
        String key1 = Constants.seckillStockKey(id);
        String key2 = Constants.seckillBoughtKey(id);
        String resultKey = Constants.seckillResultKey(id);
        int perLimit = activity.getPerLimit() != null && activity.getPerLimit() > 0 ? activity.getPerLimit() : 1;
        Duration seckillKeyTtl = Constants.seckillKeyTtl(activity.getEndTime());
        // ARGV 用 StringRedisSerializer：GenericJacksonJsonRedisSerializer 默认带 @class 类型信息，
        // 会把 "1" 序列化成 "java.lang.String":"\"1\""，Lua 端 tonumber('"1"') 返回 nil → 触发 -3（参数非法）。
        // 这里固定走字符串序列化，Lua 才能正常解析数字
        Long result = redisTemplate.execute(
                SECKILL_SCRIPT,
                RedisSerializer.string(),
                null,
                List.of(key1, key2, resultKey),
                userId.toString(), String.valueOf(quantity), String.valueOf(perLimit),
                String.valueOf(seckillKeyTtl.getSeconds()), Constants.SECKILL_RESULT_QUEUING_JSON);
        // 脚本约定：1=成功 -1=库存不足 -2=超限购 -3=参数非法
        if (result == null) {
            throw new BusinessException(SYSTEM_ERROR);
        }
        if (result == -1) {
            throw new BusinessException(SECKILL_SOLD_OUT);
        }
        if (result == -2) {
            throw new BusinessException(SECKILL_LIMIT_REACHED);
        }
        if (result == -3) {
            throw new BusinessException(PARAM_ERROR);
        }
        //利用rocketmq异步解耦支付与秒杀
        SeckillOrderMessage msg = new SeckillOrderMessage();
        msg.setActivityId(id);
        // ⭐ 收货地址必须随消息带过去：消费者建单要用它写收货人快照，并做「地址归属 == 下单人」防越权校验。
        //    漏传时 addressMap.get(null) 恒为 null → 每笔秒杀单都命中"地址非法"分支：回补库存 + 标 FAILED，
        //    表现就是"抢到了却永远建不了单"，秒杀功能整体不可用。
        msg.setAddressId(addressId);
        // ⭐ 成交单价随消息带过去（活动秒杀价）：消费者只按 skuId 拿到 SKU 原价，
        //    不带这个字段就会用原价结算，秒杀价形同虚设、用户按原价付款。
        //    随消息携带让消费者零额外查询（批内 N 条仍是 0 次查活动表）。
        msg.setSeckillPrice(activity.getSeckillPrice());
        msg.setQuantity(quantity);
        msg.setUserId(userId);
        msg.setSkuId(activity.getSkuId());
        msg.setOrderNo(String.valueOf(snowflakeIdGenerator.nextId()));
        // 异步发送：发送线程不等 Broker 确认，抢购请求立即返回"排队中"。
        // 发送结果走回调线程——那时 HTTP 响应早已返回，onException 无法再抛异常给前端，
        // 只能把失败写进 Redis（用户轮询 getResult 能看到"下单失败"）并回补资源。
        // 同步阶段（producer 已关闭/参数非法等）仍走 catch 抛给前端，此时请求还在处理中
        try {
            rocketMQTemplate.asyncSend("seckill-order-topic", msg, new SendCallback() {
                @Override
                public void onSuccess(SendResult sendResult) {
                    // 发送成功：后续由消费者建单并写结果，无需处理
                }

                @Override
                public void onException(Throwable e) {
                    // ⭐ 回调里的"发送失败"可能是客户端超时误判——消息或许已落盘并被消费建单。
                    //    立即回补会与消费事务竞态（既建单又回补 → 库存虚增超卖），
                    //    先入延迟队列，由定时任务以 DB 订单为准绳复核后再补偿（SeckillCompensator）
                    seckillCompensator.scheduleCompensation(msg);
                }
            });
        } catch (Exception e) {
            // 同步抛出 = 消息根本没有发出去（producer 未启动/参数非法），不存在"已建单"的可能，
            // 立即补偿是安全的；仍抛给前端让本次抢购明确失败
            seckillCompensator.compensateNow(msg);
            throw new BusinessException(SYSTEM_ERROR);
        }
        SeckillResultVO vo = new SeckillResultVO();
        vo.setSeckillResult(Constants.SECKILL_RESULT_QUEUING);  // 0 = 排队中
        vo.setSeckillResultText("排队中");
        return vo;
    }

    /**
     * 3.6.8 秒杀结果查询：返回当前用户在该活动最近一次抢购的结果。
     * 同步下单模式没有"排队中"状态：Redis 有记录且订单存在 → 成功；否则 → 无抢购记录。
     */
    @Override
    public SeckillResultVO getResult(Long activityId) {
        SeckillResultVO vo = new SeckillResultVO();
        String orderNo = null;
        try {
            Object cached = redisTemplate.opsForHash()
                    .get(Constants.seckillResultKey(activityId), SecurityUtils.getUserId().toString());
            if (cached instanceof String s) {
                if (Constants.SECKILL_RESULT_QUEUING_VALUE.equals(s)) {
                    vo.setSeckillResult(Constants.SECKILL_RESULT_QUEUING);
                    vo.setSeckillResultText("排队中");
                    return vo;
                }
                if (Constants.SECKILL_RESULT_FAILED_VALUE.equals(s)) {
                    vo.setSeckillResult(Constants.SECKILL_RESULT_FAILED);
                    vo.setSeckillResultText("下单失败");
                    return vo;
                }
                orderNo = s;
            }
        } catch (Exception ignored) {
            // Redis 故障按无记录处理，用户可在订单列表核实
        }
        if (orderNo == null) {
            vo.setSeckillResult(Constants.SECKILL_RESULT_NONE);
            vo.setSeckillResultText("无抢购记录");
            return vo;
        }
        Order order = orderService.getOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getOrderNo, orderNo), false);
        if (order == null) {
            vo.setSeckillResult(Constants.SECKILL_RESULT_NONE);
            vo.setSeckillResultText("无抢购记录");
            return vo;
        }
        vo.setSeckillResult(Constants.SECKILL_RESULT_SUCCESS);
        vo.setSeckillResultText("下单成功");
        vo.setOrderNo(order.getOrderNo());
        vo.setOrderStatus(order.getOrderStatus());
        vo.setPayAmount(order.getPayAmount());
        return vo;
    }

    /**
     * 4.7.5 后台秒杀活动列表：刻意不走缓存——管理端低频访问，且 soldQuantity/redisStock 是对账字段，
     * 必须实时；缓存 60 秒的滞后会让对账失去意义。状态口径与 C 端列表共用 computeStatus，避免两处口径漂移。
     */
    @Override
    public PageResult<SeckillAdminVO> listSeckills(Integer status, Integer pageNum, Integer pageSize) {
        // status 只接受 0/1/2/null（不传=全部），页码从 1 起，pageSize 上限 100
        if (status != null && (status < 0 || status > 2)) {
            throw new BusinessException(PARAM_ERROR);
        }
        int page = (pageNum == null || pageNum < 1) ? 1 : pageNum;
        int size = (pageSize == null || pageSize < 1) ? 10 : Math.min(pageSize, Constants.MAX_PAGE_SIZE);

        List<SeckillAdminVO> all = seckillActivityMapper.selectAdminList();
        LocalDateTime now = LocalDateTime.now();
        List<SeckillAdminVO> filtered = new ArrayList<>(all.size());
        for (SeckillAdminVO vo : all) {
            int current = computeStatus(vo.getStartTime(), vo.getEndTime(), now);
            vo.setActivityStatus(current);
            vo.setActivityStatusText(statusText(current));
            vo.setServerTime(now);
            if (status == null || status == current) {
                filtered.add(vo);
            }
        }
        // SQL 已 ORDER BY start_time DESC，过滤保序，无需二次排序
        long total = filtered.size();
        int from = Math.min((page - 1) * size, filtered.size());
        int to = Math.min(from + size, filtered.size());
        List<SeckillAdminVO> pageList = new ArrayList<>(filtered.subList(from, to));
        // 对账字段只装饰当前页，省 N 次 Redis 读
        pageList.forEach(this::overlayReconcileStock);
        return PageResult.of(pageList, total, page, size);
    }

    /**
     * 4.7.6 创建秒杀活动。格式类约束（缺参/非正数）已由 DTO 上的声明式注解 + @Valid 完成，
     * 这里只做依赖运行时上下文的业务规则：
     * SKU 存在且上架、秒杀价须小于原价、start < end、同一 SKU 无时间重叠的未结束活动。
     * <p>
     * 预热时机的取舍：Redis 写在事务提交前执行（代理在方法 return 后才 commit），
     * 若事务最终回滚，DB 无此活动而 Redis 可能残留库存 key——因此在预热失败抛错回滚前，
     * 补偿删除本活动相关的 key；预热的"写后失败"窗口极小，Demo 接受这个残差。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SeckillAdminVO addSeckill(SeckillCreateDTO dto) {
        // 1. 业务校验
        ProductSku sku = productSkuMapper.selectById(dto.getSkuId());
        if (sku == null) {
            throw new BusinessException(NOT_FOUND);
        }
        if (sku.getStatus() == null || sku.getStatus() != Constants.PRODUCT_STATUS_ON_SHELF) {
            throw new BusinessException(PARAM_ERROR);       // 须存在且上架（4.7.6 规则 1）
        }
        if (dto.getSeckillPrice().compareTo(sku.getPrice()) >= 0) {
            throw new BusinessException(PARAM_ERROR);       // 秒杀价须严格小于原价
        }
        if (!dto.getEndTime().isAfter(dto.getStartTime())) {
            throw new BusinessException(PARAM_ERROR);       // end 须晚于 start
        }
        // 同一 SKU 不允许与未结束活动时间重叠：已存在活动的 end > 新活动 start 且 start < 新活动 end 即重叠
        Long overlapping = seckillActivityMapper.selectCount(new LambdaQueryWrapper<SeckillActivity>()
                .eq(SeckillActivity::getSkuId, dto.getSkuId())
                .gt(SeckillActivity::getEndTime, dto.getStartTime())
                .lt(SeckillActivity::getStartTime, dto.getEndTime()));
        if (overlapping != null && overlapping > 0) {
            throw new BusinessException(PARAM_ERROR);       // 同 SKU 同时段重叠
        }

        // 2. 落库：available_stock = total_stock；perLimit 缺省兜底 1（与 Lua 侧口径一致）
        SeckillActivity activity = new SeckillActivity();
        activity.setActivityName(dto.getActivityName().trim());
        activity.setSkuId(dto.getSkuId());
        activity.setProductId(sku.getProductId());
        activity.setSeckillPrice(dto.getSeckillPrice());
        activity.setTotalStock(dto.getTotalStock());
        activity.setAvailableStock(dto.getTotalStock());
        activity.setPerLimit(dto.getPerLimit() == null ? 1 : dto.getPerLimit());
        activity.setStartTime(dto.getStartTime());
        activity.setEndTime(dto.getEndTime());
        activity.setDeleted(Constants.NOT_DELETED);
        activity.setCreatedAt(LocalDateTime.now());
        activity.setUpdatedAt(LocalDateTime.now());
        seckillActivityMapper.insert(activity);

        // 3. 预热：库存计数、清已购容器、布隆放行、逐出 C 端列表缓存（新活动立刻可见）
        try {
            // TTL = 到活动 end_time + 缓冲 的剩余时长：活动结束后库存 key 自动回收，不再永久滞留。
            // （原先无 TTL，活动自然结束后若无人调 stopSeckill，这两个 key 会一直留在 Redis 里）
            Duration ttl = Constants.seckillKeyTtl(activity.getEndTime());
            redisTemplate.opsForValue().set(
                    Constants.seckillStockKey(activity.getId()), activity.getAvailableStock(), ttl);
            redisTemplate.delete(Constants.seckillBoughtKey(activity.getId()));
            bloomFilterRegistry.add(Constants.BLOOM_FILTER_SECKILL, activity.getId());
            redisTemplate.delete(Constants.SECKILL_LIST_KEY);
        } catch (Exception e) {
            // 预热失败=活动在 Redis 层不可抢（Lua 对 key 不存在一律拒绝），此时不应允许创建：
            // 抛出让 DB 回滚，并清除可能已写入的半成品 key（写库成功但提交前的数据不对外可见，可安全清）
            try {
                redisTemplate.delete(Constants.seckillStockKey(activity.getId()));
                redisTemplate.delete(Constants.seckillBoughtKey(activity.getId()));
            } catch (Exception ignored) {
            }
            throw new BusinessException(SYSTEM_ERROR);
        }

        // 4. 返回创建结果（供前端跳转/展示；对账字段即初始值）
        SeckillAdminVO vo = new SeckillAdminVO();
        vo.setId(activity.getId());
        vo.setActivityName(activity.getActivityName());
        vo.setSkuId(activity.getSkuId());
        vo.setProductId(activity.getProductId());
        vo.setSeckillPrice(activity.getSeckillPrice());
        vo.setTotalStock(activity.getTotalStock());
        vo.setAvailableStock(activity.getAvailableStock());
        vo.setSoldQuantity(0);
        vo.setRedisStock(activity.getTotalStock());
        vo.setPerLimit(activity.getPerLimit());
        vo.setStartTime(activity.getStartTime());
        vo.setEndTime(activity.getEndTime());
        vo.setCreatedAt(activity.getCreatedAt());
        return vo;
    }

    /**
     * 4.7.7 终止秒杀活动：先断 Redis 弹药（安全方向的失败：最坏是"DB 还在进行中但抢不了"），
     * 再改 DB 状态。布隆过滤器只加不删（位共享特性），已终止活动的放行由 DB/详情查询兜底。
     */
    @Override
    public void stopSeckill(Long id) {
        // 1. 删除购买路径的 Redis 数据：stock 没了 Lua 立即快速失败，bought 顺带清容器
        redisTemplate.delete(Constants.seckillStockKey(id));
        redisTemplate.delete(Constants.seckillBoughtKey(id));
        redisTemplate.delete(Constants.seckillResultKey(id));
        // 2. 逐出详情/列表缓存：否则最长 60 秒内页面仍显示"进行中 + 倒计时"
        redisTemplate.delete(Constants.SECKILL_DETAIL_KEY_PREFIX + id);
        redisTemplate.delete(Constants.SECKILL_LIST_KEY);
        // 3. 落库终止状态（seckill() 入口读它报 42102）；影响行数 0 = 活动不存在或已删除
        int rows = seckillActivityMapper.stopSeckillById(id);
        if (rows == 0) {
            throw new BusinessException(NOT_FOUND);
        }
    }

    /**
     * 后台对账字段：redisStock 实时读 Redis 预扣余量（seckill:stock:{id}）。
     * key 不存在（未预热/活动已终止删除）或 Redis 故障时置 null——不能用 DB 值冒充，
     * 否则运营会把"读不到"误当成"Redis 与 DB 一致"，对账前提即被破坏。
     */
    private void overlayReconcileStock(SeckillAdminVO vo) {
        try {
            Object stock = redisTemplate.opsForValue().get(Constants.seckillStockKey(vo.getId()));
            if (stock instanceof Number n) {
                vo.setRedisStock(n.intValue());
            } else if (stock instanceof String s && !s.isBlank()) {
                vo.setRedisStock(Integer.parseInt(s.trim()));
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * 全量加载：整表缓存（60s）→ 未命中走 SQL（status 传 null 取全量）→ 回填缓存。
     * Redis 读故障静默降级直查 SQL；空列表同样缓存（防"无活动"时每次请求都回源）。
     */
    private List<SeckillActivityVO> loadAll() {
        try {
            Object cached = redisTemplate.opsForValue().get(Constants.SECKILL_LIST_KEY);
            if (cached instanceof List<?> raw) {
                List<SeckillActivityVO> all = new ArrayList<>(raw.size());
                boolean valid = true;
                for (Object o : raw) {
                    if (!(o instanceof SeckillActivityVO vo)) {
                        valid = false;      // 元素类型不符 = 脏缓存（版本升级残留等），放弃并走重建覆盖
                        break;
                    }
                    all.add(vo);
                }
                if (valid) {
                    return all;
                }
            }
        } catch (Exception ignored) {
            // Redis 不可用：视为未命中，降级查库（缓存层故障不阻断业务）
        }

        List<SeckillActivityVO> all = seckillActivityMapper.selectSeckillList(null);
        try {
            redisTemplate.opsForValue().set(Constants.SECKILL_LIST_KEY, all, Constants.TTL_SECKILL_LIST);
        } catch (Exception ignored) {
            // 回填失败不影响本次响应，仅损失一次缓存写入
        }
        return all;
    }

    /** 活动状态实时计算：now < start 未开始；now > end 已结束；否则进行中（缺时间字段按进行中兜底） */
    private int computeStatus(LocalDateTime start, LocalDateTime end, LocalDateTime now) {
        if (start != null && now.isBefore(start)) {
            return 0;
        }
        if (end != null && now.isAfter(end)) {
            return 2;
        }
        return 1;
    }

    /**
     * VO 装饰（列表与详情共用，保证两处状态口径一致）：实时计算状态 + 状态文本 + 服务器时间。
     * 返回状态值供调用方过滤。不含库存装饰——列表只需装饰当前页，由调用方按需调 overlayLiveStock。
     */
    private int decorate(SeckillActivityVO vo, LocalDateTime now) {
        int current = computeStatus(vo.getStartTime(), vo.getEndTime(), now);
        vo.setActivityStatus(current);
        vo.setActivityStatusText(statusText(current));
        vo.setServerTime(now);
        return current;
    }

    private String statusText(int status) {
        return switch (status) {
            case 0 -> "未开始";
            case 1 -> "进行中";
            default -> "已结束";
        };
    }

    /**
     * 剩余库存实时装饰：GET seckill:stock:{id}。
     * Redis 值优先，因为它还包含"已预扣但尚未建单"的部分（消费者异步落库有秒级延迟），
     * 比 DB 的 available_stock 更贴近用户视角的真实余量。
     * key 不存在（未预热/活动已终止被清理）或 Redis 故障时，保留 SQL 查出的 DB 值兜底——
     * DB 值现在也会随建单/取消/退款一起变动，两者互为参照。
     */
    private void overlayLiveStock(SeckillActivityVO vo) {
        try {
            Object stock = redisTemplate.opsForValue().get(Constants.seckillStockKey(vo.getId()));
            if (stock instanceof Number n) {
                vo.setAvailableStock(n.intValue());
            } else if (stock instanceof String s && !s.isBlank()) {
                vo.setAvailableStock(Integer.parseInt(s.trim()));
            }
        } catch (Exception ignored) {
            // Redis 故障：保留 DB 的 available_stock 兜底
        }
    }
}
