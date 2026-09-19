package com.mall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.github.pagehelper.PageInfo;
import com.mall.common.BusinessException;
import com.mall.common.PageUtils;
import com.mall.common.Constants;
import com.mall.common.SnowflakeIdGenerator;
import com.mall.dto.AddressDTO;
import com.mall.dto.OrderCreateDTO;
import com.mall.dto.OrderSkuDTO;
import com.mall.dto.SeckillOrderMessage;
import com.mall.entity.*;
import com.mall.mapper.CartMapper;
import com.mall.mapper.OrderItemMapper;
import com.mall.mapper.OrderMapper;
import com.mall.mapper.ProductMapper;
import com.mall.mapper.ProductSkuMapper;
import com.mall.mapper.SeckillActivityMapper;
import com.mall.mapper.StockLogMapper;
import com.mall.mapper.UserAddressMapper;
import com.mall.mapper.UserCouponMapper;
import com.mall.service.IOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mall.service.IUserService;
import com.mall.util.SeckillStockRestorer;
import com.mall.util.SecurityUtils;
import com.mall.vo.*;

import io.swagger.v3.oas.annotations.Operation;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.math.BigDecimal;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static com.mall.enums.ErrorCode.*;


/**
 * <p>
 * 订单主表 服务实现类
 * </p>
 *
 * @author 乐乐
 * @since 2026-08-16
 */
@Service
public class OrderServiceImpl extends ServiceImpl<OrderMapper, Order> implements IOrderService {
    private final OrderMapper orderMapper;
    private final IUserService userService;
    private final CartMapper cartMapper;
    private final ProductSkuMapper productSkuMapper;
    private final UserAddressMapper userAddressMapper;
    private final OrderItemMapper orderItemMapper;
    private final StockLogMapper stockLogMapper;
    private final ProductMapper productMapper;
    private final SnowflakeIdGenerator idGenerator;
    /** 秒杀活动库存条件扣减（消费端建单成功后执行，DB 侧对账口径） */
    private final SeckillActivityMapper seckillActivityMapper;
    /** 秒杀库存回补（取消/超时把这笔单占用的秒杀库存还回去） */
    private final SeckillStockRestorer seckillStockRestorer;
    /** 优惠券核销/校验（下单用券） */
    private final UserCouponMapper userCouponMapper;

    private static final Logger log = LoggerFactory.getLogger(OrderServiceImpl.class);

    private String generateOrderNo() {
        return String.valueOf(idGenerator.nextId());
    }
    public OrderServiceImpl(OrderMapper orderMapper,
                            IUserService userService,
                            CartMapper cartMapper,
                            ProductSkuMapper productSkuMapper,
                            UserAddressMapper userAddressMapper,
                            OrderItemMapper orderItemMapper,
                            StockLogMapper stockLogMapper,
                            ProductMapper productMapper,
            SnowflakeIdGenerator idGenerator,
            SeckillActivityMapper seckillActivityMapper,
            SeckillStockRestorer seckillStockRestorer,
            UserCouponMapper userCouponMapper) {
        this.orderMapper = orderMapper;
        this.userService = userService;
        this.cartMapper =  cartMapper;
        this.productSkuMapper = productSkuMapper;
        this.userAddressMapper = userAddressMapper;
        this.orderItemMapper = orderItemMapper;
        this.stockLogMapper = stockLogMapper;
        this.productMapper = productMapper;
        this.idGenerator = idGenerator;
        this.seckillActivityMapper = seckillActivityMapper;
        this.seckillStockRestorer = seckillStockRestorer;
        this.userCouponMapper = userCouponMapper;
    }

    @Override
    public OrderPreviewVO getOrderPreview() {
        Long userId = SecurityUtils.getUserId();
        ArrayList<AddressVO> addressVOArrayList = userService.listAddresses();
        List<OrderPreviewItemVO> items = cartMapper.selectCheckedItems(userId);
        BigDecimal totalAmount = items.stream()
                .filter(i -> i.getPrice() != null)
                .map(i -> i.getPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        OrderAmountVO amount = new OrderAmountVO();
        amount.setTotalAmount(totalAmount);
        amount.setFreightAmount(BigDecimal.ZERO);          // demo 免运费
        amount.setPayAmount(totalAmount.add(BigDecimal.ZERO));

        // 4. 组装返回
        OrderPreviewVO vo = new OrderPreviewVO();
        vo.setAddresses(addressVOArrayList);
        vo.setItems(items);
        vo.setAmount(amount);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @Operation( summary = "创建订单")
    public OrderCreateVO addOrder(@NonNull OrderCreateDTO orderCreateDTO) {
        Long userId = SecurityUtils.getUserId();
        OrderCreateVO vo = addOrder(orderCreateDTO, userId);
        // 6. 清理购物车中已勾选的项（仅普通下单路径：秒杀单不走购物车，
        //    若在公共方法里执行，会把用户已勾选未结算的其他商品误删）
        cartMapper.delete(new QueryWrapper<com.mall.entity.Cart>()
                .eq("user_id", userId)
                .eq("checked", Constants.CHECKED));
        return vo;
    }

    /**
     * 显式指定下单用户的建单入口。MQ 消费线程没有 SecurityContext
     * （JwtAuthenticationFilter 不在 RocketMQ 消费线程上运行，ThreadLocal 为空），
     * 必须由调用方显式传入 userId，不得依赖 SecurityUtils.getUserId()。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderCreateVO addOrder(@NonNull OrderCreateDTO orderCreateDTO, Long userId) {
        // 1. 校验收货地址存在且属于当前用户（防越权）
        UserAddress address = userAddressMapper.selectById(orderCreateDTO.getAddressId());
        if (address == null || !address.getUserId().equals(userId)) {
            throw new BusinessException(NOT_FOUND);
        }

        // 2. 订单号：客户端传了 orderNo 就当幂等键用（重复提交返回原单，不再扣库存），
        //    缺省仍由后端雪花生成。审计 高-5：DTO.orderNo 此前被完全忽略，
        //    结算页双击/网络重试会生成两笔订单并重复扣库存
        String orderNo = orderCreateDTO.getOrderNo();
        if (orderNo != null && !orderNo.isBlank()) {
            // 白名单校验：幂等键只允许字母数字/下划线/连字符，防怪字符与超长值撞索引
            if (!orderNo.matches("[A-Za-z0-9_-]{8,32}")) {
                throw new BusinessException(PARAM_ERROR);
            }
            Order exist = orderMapper.selectOne(new QueryWrapper<Order>()
                    .eq("order_no", orderNo)
                    .eq("user_id", userId));
            if (exist != null) {
                return buildCreateVO(exist);
            }
        } else {
            orderNo = generateOrderNo();
        }
        List<OrderSkuDTO> skuList = orderCreateDTO.getSkuList();
        // 防御性校验：skuList 为空会导致下方遍历 NPE（Controller 层 @NotEmpty 已拦截，此处兜底）
        if (skuList == null || skuList.isEmpty()) {
            throw new BusinessException(PARAM_ERROR);
        }

        // 3. 批量预读 + 校验（H1：3N 次查询 → 2 次；H2：补上架校验）。
        //    同 SKU 的多行购买数量先合并：同一 SKU 只扣一次锁一次、只写一条流水。
        //    TreeMap 按 skuId 升序——并发下单对同一组 SKU 的加锁顺序全局一致，
        //    A 扣 [1,2]、B 扣 [2,1] 互等对方行锁的 AB-BA 死锁从此没有立足点
        TreeMap<Long, Integer> qtyBySku = new TreeMap<>();
        for (OrderSkuDTO item : skuList) {
            if (item.getSkuId() == null || item.getQuantity() == null || item.getQuantity() < 1) {
                throw new BusinessException(PARAM_ERROR);
            }
            qtyBySku.merge(item.getSkuId(), item.getQuantity(), Integer::sum);
        }
        // selectBatchIds 自动追加 deleted=0（@TableLogic）：已删除/不存在的 SKU 在这里直接查不到
        Map<Long, ProductSku> skuMap = productSkuMapper.selectBatchIds(qtyBySku.keySet())
                .stream().collect(Collectors.toMap(ProductSku::getId, s -> s));
        if (skuMap.size() != qtyBySku.size()) {
            throw new BusinessException(STOCK_NOT_ENOUGH);
        }
        // H2 上架校验：商品未上架（含被删后查不到）或 SKU 未上架都拒绝，错误口径与购物车一致（2001）
        Map<Long, Product> productMap = productMapper.selectBatchIds(
                        skuMap.values().stream().map(ProductSku::getProductId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Product::getId, p -> p));
        for (ProductSku sku : skuMap.values()) {
            Product product = productMap.get(sku.getProductId());
            if (product == null || product.getStatus() == null
                    || product.getStatus() != Constants.PRODUCT_STATUS_ON_SHELF
                    || sku.getStatus() == null
                    || sku.getStatus() != Constants.PRODUCT_STATUS_ON_SHELF) {
                throw new BusinessException(PRODUCT_OFF_SHELF);
            }
        }

        // 4. 金额预计算：价格已在预读快照里，主表一次插入即带全金额
        //    （消掉旧版"先插 0 再 updateById 整行回填"那条 UPDATE）
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (Map.Entry<Long, Integer> entry : qtyBySku.entrySet()) {
            totalAmount = totalAmount.add(skuMap.get(entry.getKey()).getPrice()
                    .multiply(BigDecimal.valueOf(entry.getValue())));
        }

        // 4.5 优惠券校验 + 优惠计算（修复前 couponId 被完全忽略：无折扣、无核销，审计 高-4/H3）。
        //     口径与 3.8 可用券列表一致：本人 + 未使用 + 未过期；满减券比对门槛；折扣券(type=3)暂不开放
        BigDecimal discountAmount = BigDecimal.ZERO;
        if (orderCreateDTO.getCouponId() != null) {
            UserCouponVO redeemable = userCouponMapper.selectRedeemable(orderCreateDTO.getCouponId(), userId);
            if (redeemable == null || redeemable.getType() == null || redeemable.getDiscountAmount() == null) {
                throw new BusinessException(COUPON_NOT_USABLE);      // 不存在/非本人/已用/已过期
            }
            if (redeemable.getType() == 1
                    && (redeemable.getThresholdAmount() == null
                        || totalAmount.compareTo(redeemable.getThresholdAmount()) < 0)) {
                throw new BusinessException(COUPON_NOT_USABLE);      // 满减门槛未达
            }
            if (redeemable.getType() != 1 && redeemable.getType() != 2) {
                throw new BusinessException(COUPON_NOT_USABLE);      // type=3 折扣券暂不开放
            }
            // 封顶：优惠不超过商品总额，payAmount 永不为负
            discountAmount = redeemable.getDiscountAmount().min(totalAmount);
        }

        // 5. 插入订单主表（幂等重放 / DuplicateKey 兜底同旧版）
        Order order = new Order();
        order.setOrderNo(orderNo)
                .setUserId(userId)
                .setTotalAmount(totalAmount)
                .setDiscountAmount(discountAmount)
                .setCouponId(orderCreateDTO.getCouponId())
                .setPayAmount(totalAmount.subtract(discountAmount))
                .setFreightAmount(BigDecimal.ZERO)
                .setOrderStatus(Constants.ORDER_STATUS_UNPAID)
                .setPaymentStatus(0)
                .setReceiverName(address.getReceiverName())
                .setReceiverPhone(address.getReceiverPhone())
                .setReceiverProvince(address.getProvince())
                .setReceiverCity(address.getCity())
                .setReceiverDistrict(address.getDistrict())
                .setReceiverAddress(address.getDetailAddress())
                .setRemark(orderCreateDTO.getRemark())
                .setCreatedAt(LocalDateTime.now())
                .setUpdatedAt(LocalDateTime.now())
                .setDeleted(Constants.NOT_DELETED);
        try {
            orderMapper.insert(order);
        } catch (DuplicateKeyException e) {
            // 并发双击：两个请求都通过了上面的存在性检查，uk_order_no 唯一索引只放行一个。
            // 重查返回原单（限定本 user_id：拿别人的 orderNo 撞索引只会得到"参数错误"，不会泄露他人订单）。
            // 重复键只失败当前语句、事务继续，后续扣库存正常提交
            Order exist = orderMapper.selectOne(new QueryWrapper<Order>()
                    .eq("order_no", orderNo)
                    .eq("user_id", userId));
            if (exist == null) {
                throw new BusinessException(PARAM_ERROR);
            }
            return buildCreateVO(exist);
        }

        // 6. CAS 扣库存（锁序已定）+ 批量写明细/流水。
        //    任一 SKU 库存不足 → 抛异常整个事务回滚（主表插入一并撤销，原子性与旧版相同）
        LocalDateTime now = LocalDateTime.now();
        List<OrderItem> orderItems = new ArrayList<>(qtyBySku.size());
        List<StockLog> stockLogs = new ArrayList<>(qtyBySku.size());
        for (Map.Entry<Long, Integer> entry : qtyBySku.entrySet()) {
            ProductSku sku = skuMap.get(entry.getKey());
            int quantity = entry.getValue();
            // 6.1 原子扣减库存（CAS）：stock >= qty 才允许，并发下不会超卖
            if (productSkuMapper.deductStock(sku.getId(), quantity) == 0) {
                throw new BusinessException(STOCK_NOT_ENOUGH);
            }
            // 6.2 订单明细（商品名等快照直接取自预读结果，不再逐个 selectById）
            OrderItem orderItem = new OrderItem();
            orderItem.setOrderId(order.getId());
            orderItem.setOrderNo(orderNo);
            orderItem.setProductId(sku.getProductId());
            orderItem.setSkuId(sku.getId());
            Product productSnapshot = productMap.get(sku.getProductId());
            orderItem.setProductName(productSnapshot != null ? productSnapshot.getProductName() : "");
            orderItem.setSkuSpecs(sku.getSpecs());
            orderItem.setSkuImage(sku.getImage());
            orderItem.setPrice(sku.getPrice());
            orderItem.setQuantity(quantity);
            orderItem.setTotalAmount(sku.getPrice().multiply(BigDecimal.valueOf(quantity)));
            orderItem.setCreatedAt(now);
            orderItem.setUpdatedAt(now);
            orderItems.add(orderItem);
            // 6.3 库存流水：before/after 用预读快照推算（旧版扣完再回读一次，纯为流水对账）
            StockLog stockLog = new StockLog();
            stockLog.setSkuId(sku.getId());
            stockLog.setOrderId(order.getId());
            stockLog.setOrderNo(orderNo);
            stockLog.setChangeType(Constants.STOCK_CHANGE_ORDER);
            stockLog.setChangeQty(-quantity);
            stockLog.setBeforeStock(sku.getStock());
            stockLog.setAfterStock(sku.getStock() - quantity);
            stockLog.setCreatedAt(now);
            stockLogs.add(stockLog);
        }
        orderItemMapper.insertBatch(orderItems);
        stockLogMapper.insertBatch(stockLogs);

        // 7. 优惠券核销（CAS）放最后一步：并发用同一张券只有一人成功，
        //    败者抛异常整单回滚（订单/扣减/明细一并撤销，不会"没享优惠却烧了券"）。
        //    产品规则：核销后不返还——取消/超时/退款均不退券
        if (orderCreateDTO.getCouponId() != null
                && userCouponMapper.redeem(orderCreateDTO.getCouponId(), userId, orderNo) == 0) {
            throw new BusinessException(COUPON_NOT_USABLE);          // 并发被抢先核销
        }

        // 8. 组装响应
        return buildCreateVO(order);
    }

    /** 下单响应组装（幂等重放路径复用） */
    private OrderCreateVO buildCreateVO(Order order) {
        OrderCreateVO vo = new OrderCreateVO();
        vo.setOrderNo(order.getOrderNo());
        vo.setOrderId(order.getId());
        vo.setOrderStatus(order.getOrderStatus());
        vo.setPayAmount(order.getPayAmount());
        vo.setDiscountAmount(order.getDiscountAmount());
        vo.setCreatedAt(order.getCreatedAt());
        return vo;
    }

    /**
     * 秒杀建单：整个入参共用一个数据库事务（由 MQ 消费端调用）。
     *
     * <p><b>实际调用形态是"单条"</b>：消费端 {@code SeckillOrderConsumer} 声明的是
     * {@code RocketMQListener<SeckillOrderMessage>}，每次只传一个元素。
     * 保留 List 签名与下面的批量优化，是为了将来真的换成批量消费（需绕开 rocketmq-spring 容器，
     * 见 SeckillOrderConsumer 类注释）时无需改这里。
     *
     * 优化点（相对逐条 addOrder）：
     * 1. SKU/商品快照/收货地址各一次批量预读（3 次往返替代 3×N 次）；
     * 2. 同 SKU 汇总一次扣库存，热点行只锁一次；汇总失败（库存不足）再逐条试扣定位；
     * 3. 订单主表逐条插入（拿自增 id + 逐单隔离重复消息），明细/流水攒批各一次插入。
     *
     * 失败隔离约定：
     * - 单条消息业务失败（地址非法/插入异常）：替它扣的 DB 库存当场回补，
     *   orderNo 进失败集合，由消费端做 Redis 回补与 FAILED 标记；
     * - 重复消息（orderNo 已存在且同一个 userId）：视为成功，<b>DB 库存不动</b>
     *   （那笔库存在上一轮投递时已经扣过，再回补就是白送库存）；
     * - orderNo 已存在但归属不同（snowflake worker-id 重复导致的 ID 冲突）：按失败处理并告警，
     *   绝不能当成重复消息——否则用户被写"下单成功"却查到别人的订单；
     * - 整批性异常（DB 宕机等）：直接抛出，事务整体回滚，MQ 重投整批。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Set<String> addSeckillOrderBatch(List<SeckillOrderMessage> msgs) {
        if (msgs == null || msgs.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> failed = new HashSet<>();
        LocalDateTime now = LocalDateTime.now();

        // 0. 批量预读：3 次往返拿全批的 SKU / 商品快照 / 收货地址
        Map<Long, ProductSku> skuMap = productSkuMapper.selectBatchIds(
                        msgs.stream().map(SeckillOrderMessage::getSkuId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(ProductSku::getId, s -> s));
        Map<Long, Product> productMap = productMapper.selectBatchIds(
                        skuMap.values().stream().map(ProductSku::getProductId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Product::getId, p -> p));
        Map<Long, UserAddress> addressMap = userAddressMapper.selectBatchIds(
                        msgs.stream().map(SeckillOrderMessage::getAddressId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(UserAddress::getId, a -> a));

        // 1. 按 SKU 汇总扣库存：一批只拿一次热点行锁
        Map<Long, List<SeckillOrderMessage>> bySku = msgs.stream()
                .collect(Collectors.groupingBy(SeckillOrderMessage::getSkuId,
                        LinkedHashMap::new, Collectors.toList()));
        List<SeckillOrderMessage> secured = new ArrayList<>();
        for (Map.Entry<Long, List<SeckillOrderMessage>> group : bySku.entrySet()) {
            ProductSku sku = skuMap.get(group.getKey());
            if (sku == null) {
                group.getValue().forEach(m -> failed.add(m.getOrderNo()));
                continue;
            }
            int total = group.getValue().stream()
                    .mapToInt(SeckillOrderMessage::getQuantity).sum();
            if (productSkuMapper.deductStock(group.getKey(), total) == 1) {
                secured.addAll(group.getValue());
                continue;
            }
            // 汇总扣失败（库存不足）→ 逐条试扣，定位哪些单还能成
            for (SeckillOrderMessage m : group.getValue()) {
                if (productSkuMapper.deductStock(group.getKey(), m.getQuantity()) == 1) {
                    secured.add(m);
                } else {
                    failed.add(m.getOrderNo());
                }
            }
        }

        // 2. 逐单插入主表（自增 id 回填 + 逐单隔离），明细/流水攒批
        List<OrderItem> items = new ArrayList<>();
        List<StockLog> logs = new ArrayList<>();
        // 流水 before/after 为批内推算值：并发下绝对值可能漂移，对账以实际扣减为准
        Map<Long, Integer> runningStock = new HashMap<>();
        // 本批真正建单成功的秒杀数量：activityId → 每单数量列表。
        // 刻意保留"逐单"粒度而不是直接求和，汇总扣失败时才可能逐单兜底重试
        // （见 deductSeckillActivityStock）。LinkedHashMap 让扣减顺序与消息顺序一致，便于排查
        Map<Long, List<Integer>> seckillDeduct = new LinkedHashMap<>();
        for (SeckillOrderMessage msg : secured) {
            ProductSku sku = skuMap.get(msg.getSkuId());
            UserAddress address = addressMap.get(msg.getAddressId());
            // 地址防越权校验：消息里的 userId 必须与地址归属一致（与单条路径同语义）
            if (address == null || !address.getUserId().equals(msg.getUserId())) {
                productSkuMapper.restoreStock(sku.getId(), msg.getQuantity());
                failed.add(msg.getOrderNo());
                continue;
            }
            // ⭐ 成交单价取活动秒杀价（生产端随消息带来）：秒杀单必须按活动价结算，
            //    直接拿 sku.getPrice() 会按 SKU 原价扣款，秒杀价完全失效。
            //    null 兜底是为了兼容"加字段前已在队列里的老消息"，避免反序列化缺字段导致建单异常。
            BigDecimal unitPrice = msg.getSeckillPrice() != null ? msg.getSeckillPrice() : sku.getPrice();
            BigDecimal payAmount = unitPrice.multiply(BigDecimal.valueOf(msg.getQuantity()));
            Order order = new Order();
            order.setOrderNo(msg.getOrderNo())
                    .setUserId(msg.getUserId())
                    .setTotalAmount(payAmount)
                    .setPayAmount(payAmount)
                    .setFreightAmount(BigDecimal.ZERO)
                    .setOrderStatus(Constants.ORDER_STATUS_UNPAID)
                    .setPaymentStatus(0)
                    // ⭐ 标记来源：取消/超时/退款要靠它判断"这笔单是否占用了秒杀活动库存"。
                    //    漏标记的话，明细里 SKU 恰好挂着活动、且下单时间落在活动窗内的普通订单，
                    //    被取消时会被误判成秒杀单白回补一次秒杀库存（虚增 → 超卖风险）。
                    .setOrderSource(Constants.ORDER_SOURCE_SECKILL)
                    .setReceiverName(address.getReceiverName())
                    .setReceiverPhone(address.getReceiverPhone())
                    .setReceiverProvince(address.getProvince())
                    .setReceiverCity(address.getCity())
                    .setReceiverDistrict(address.getDistrict())
                    .setReceiverAddress(address.getDetailAddress())
                    .setCreatedAt(now)
                    .setUpdatedAt(now)
                    .setDeleted(Constants.NOT_DELETED);
            // ⭐ 先显式判重，不靠 DuplicateKeyException 兜：那个异常分不清"重复消息"与"其它唯一键冲突"，
            //    会把真失败也当成成功（退回库存却不进 failed 集合 → 消费者回写"下单成功" → 库存虚增）。
            //    先取现有订单，语义明确、与"撞的是哪个唯一键"无关。
            Order existing = orderMapper.selectOne(new LambdaQueryWrapper<Order>()
                    .eq(Order::getOrderNo, msg.getOrderNo())
                    .last("limit 1"));
            if (existing != null) {
                if (Objects.equals(existing.getUserId(), msg.getUserId())) {
                    // 真重复消息（MQ at-least-once 重投）：订单已建，视为成功。
                    // 库存"不动"很重要——本批替它扣的 DB 库存在上一轮投递时就已扣过，
                    // 再 restore 一次就是白送库存。活动库存在 insert 成功后才累加，同样不会被重复扣。
                    continue;
                }
                // orderNo 撞了别人的订单：多实例 snowflake worker-id 相同会生成相同 ID。
                // 这不是重复消息，必须走失败补偿，否则用户被写"下单成功"却查到别人的订单
                log.error("秒杀 orderNo 冲突且归属不同（疑似 snowflake worker-id 重复），"
                                + "orderNo={}, 消息userId={}, 已存在userId={}",
                        msg.getOrderNo(), msg.getUserId(), existing.getUserId());
                productSkuMapper.restoreStock(sku.getId(), msg.getQuantity());
                failed.add(msg.getOrderNo());
                continue;
            }
            try {
                orderMapper.insert(order);
            } catch (DuplicateKeyException e) {
                // 竞态兜底：上面的 selectOne 与 insert 之间被同 orderNo 的并发消息抢先。
                // 重新查一次确认归属：同一用户=重复消息（视为成功，不动库存）；否则按失败补偿。
                Order raced = orderMapper.selectOne(new LambdaQueryWrapper<Order>()
                        .eq(Order::getOrderNo, msg.getOrderNo())
                        .last("limit 1"));
                if (raced != null && Objects.equals(raced.getUserId(), msg.getUserId())) {
                    continue;
                }
                log.error("秒杀建单唯一键冲突（竞态或 ID 冲突），orderNo={}, userId={}",
                        msg.getOrderNo(), msg.getUserId(), e);
                productSkuMapper.restoreStock(sku.getId(), msg.getQuantity());
                failed.add(msg.getOrderNo());
                continue;
            } catch (Exception e) {
                // 该单插入失败：退回替它扣的 DB 库存，不影响批内其他单
                productSkuMapper.restoreStock(sku.getId(), msg.getQuantity());
                failed.add(msg.getOrderNo());
                continue;
            }
            // ⭐ 累加秒杀扣减量必须放在 insert 成功之后：
            //    DuplicateKeyException 那条分支虽然"视为成功"，但那笔单在上一轮投递时就已经扣过活动库存了，
            //    若把累加提到 try 之外（catch 里 continue 之前）统一做，重复消息就会被扣两次活动库存。
            seckillDeduct.computeIfAbsent(msg.getActivityId(), k -> new ArrayList<>())
                    .add(msg.getQuantity());

            int before = runningStock.computeIfAbsent(sku.getId(), k -> sku.getStock());
            int after = before - msg.getQuantity();
            runningStock.put(sku.getId(), after);

            Product product = productMap.get(sku.getProductId());
            OrderItem orderItem = new OrderItem();
            orderItem.setOrderId(order.getId());
            orderItem.setOrderNo(msg.getOrderNo());
            orderItem.setProductId(sku.getProductId());
            orderItem.setSkuId(sku.getId());
            orderItem.setProductName(product != null ? product.getProductName() : "");
            orderItem.setSkuSpecs(sku.getSpecs());
            orderItem.setSkuImage(sku.getImage());
            orderItem.setPrice(unitPrice);
            orderItem.setQuantity(msg.getQuantity());
            orderItem.setTotalAmount(payAmount);
            orderItem.setCreatedAt(now);
            orderItem.setUpdatedAt(now);
            items.add(orderItem);

            StockLog stockLog = new StockLog();
            stockLog.setSkuId(sku.getId());
            stockLog.setOrderId(order.getId());
            stockLog.setOrderNo(msg.getOrderNo());
            stockLog.setChangeType(Constants.STOCK_CHANGE_ORDER);
            stockLog.setChangeQty(-msg.getQuantity());
            stockLog.setBeforeStock(before);
            stockLog.setAfterStock(after);
            stockLog.setCreatedAt(now);
            logs.add(stockLog);
        }

        // 3. 明细/流水攒批：各 1 次往返
        if (!items.isEmpty()) {
            orderItemMapper.insertBatch(items);
        }
        if (!logs.isEmpty()) {
            stockLogMapper.insertBatch(logs);
        }

        // 4. 秒杀活动库存扣减（DB 侧对账口径）
        deductSeckillActivityStock(seckillDeduct);

        return failed;
    }

    /**
     * 秒杀活动库存条件扣减：把本批建单成功的数量按活动汇总，扣掉 DB 的 available_stock。
     *
     * <p><b>为什么扣不动也不写 failed 集合</b>：Redis 的 Lua 预扣才是防超卖的唯一权威；
     * DB 这一列按建表注释（marketing_schema.sql）的定位是「对账 + Redis 丢数据后按 DB 重新预热的兜底」。
     * 用户已经抢到了，此时因为对账字段扣不动就把他判失败（进而触发 Redis 回补、写 FAILED），
     * 属于本末倒置，所以 0 行与异常一律只记 WARN，对外仍返回"这批单全部成功"。
     *
     * <p>正常路径按活动汇总扣一次，批内同一活动只锁一次行（与 SKU 侧"同 SKU 汇总扣减"同款思路）。
     * 汇总扣不动说明 Redis 与 DB 已经漂移，再退回逐单试扣，让 DB 值尽量贴近真实，供运营在对账页发现偏差。
     * 本方法在 {@code addSeckillOrderBatch} 的事务内执行，扣减随订单一起提交/回滚。
     *
     * @param seckillDeduct activityId → 本批该活动每笔成功订单的数量（仅含真正建单成功的单）
     */
    private void deductSeckillActivityStock(Map<Long, List<Integer>> seckillDeduct) {
        for (Map.Entry<Long, List<Integer>> entry : seckillDeduct.entrySet()) {
            Long activityId = entry.getKey();
            List<Integer> perOrderQty = entry.getValue();
            int total = perOrderQty.stream().mapToInt(Integer::intValue).sum();
            try {
                if (seckillActivityMapper.deductAvailableStock(activityId, total) == 1) {
                    continue;   // 正常路径：一条 UPDATE 扣完整个批
                }
                // 兜底：逐单试扣，能扣多少算多少，尽量让 DB 值贴近真实
                int fallbackOk = 0;
                for (Integer qty : perOrderQty) {
                    if (seckillActivityMapper.deductAvailableStock(activityId, qty) == 1) {
                        fallbackOk++;
                    }
                }
                log.warn("秒杀活动库存扣减不足（Redis 与 DB 库存漂移；DB 仅作对账参考，不影响建单）：activityId={}, 批内单数={}, 兜底成功={}",
                        activityId, perOrderQty.size(), fallbackOk);
            } catch (Exception e) {
                log.warn("秒杀活动库存扣减异常（不影响建单结果）：activityId={}, 批内单数={}",
                        activityId, perOrderQty.size(), e);
            }
        }
    }

    @Override
    public PageResult<OrderListVO> getOrders(Integer pageNum, Integer pageSize, Integer orderStatus) {
        Long userId = SecurityUtils.getUserId();

        // 1. 开启分页：紧随其后的第一条 SQL 会被 PageHelper 改写
        PageUtils.startPage(pageNum, pageSize);

        // 2. 一条 SQL 联查明细，聚合商品数量 + 首条商品摘要
        List<OrderListVO> voList = orderMapper.selectOrderList(userId, orderStatus);

        // 3. 补订单状态文本
        voList.forEach(vo -> vo.setOrderStatusText(statusToText(vo.getOrderStatus())));

        // 4. 包装分页信息
        PageInfo<OrderListVO> pageInfo = new PageInfo<>(voList);
        return PageResult.of(pageInfo, voList);
    }

    @Override
    public PageResult<AdminOrderListVO> getAdminOrders(Integer pageNum, Integer pageSize,
                                                       String orderNo, String userPhone,
                                                       Integer orderStatus, Integer paymentStatus,
                                                       LocalDateTime startTime, LocalDateTime endTime) {
        // 1. 开启分页
        PageUtils.startPage(pageNum, pageSize);

        // 2. 一条 SQL 多条件联查（order + order_item 聚合 + user 手机号筛选）
        List<AdminOrderListVO> voList = orderMapper.selectAdminOrderList(
                orderNo, userPhone, orderStatus, paymentStatus, startTime, endTime);

        // 3. 补订单状态文本
        voList.forEach(vo -> vo.setOrderStatusText(statusToText(vo.getOrderStatus())));

        // 4. 包装分页信息
        PageInfo<AdminOrderListVO> pageInfo = new PageInfo<>(voList);
        return PageResult.of(pageInfo, voList);
    }

    @Override
    public OrderDetailVO getOrderDetail(String orderNo) {
        Long userId = SecurityUtils.getUserId();

        // 一条 SQL 联查，并带上 userId 做越权校验（查不到=不存在或不属于当前用户）
        OrderDetailVO vo = orderMapper.getOrderDetail(orderNo, userId);
        if (vo == null) {
            throw new BusinessException(ORDER_NOT_FOUND);
        }

        // 补订单状态文本
        vo.setOrderStatusText(statusToText(vo.getOrderStatus()));
        return vo;
    }

    @Override
    public OrderDetailVO getAdminOrderDetail(String orderNo) {
        // 管理员端：不带 userId 过滤，可查任意用户订单（含用户端已删除的）
        OrderDetailVO vo = orderMapper.getAdminOrderDetail(orderNo);
        if (vo == null) {
            throw new BusinessException(ORDER_NOT_FOUND);
        }
        vo.setOrderStatusText(statusToText(vo.getOrderStatus()));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sendOrder(String orderNo, String shippingCompany, String trackingNo) {
        // 1. 校验订单存在（后台发货，不限定用户，但过滤已删除）
        Order order = orderMapper.selectOne(
                new QueryWrapper<Order>()
                        .eq("order_no", orderNo)
                        .last("limit 1"));
        if (order == null) {
            throw new BusinessException(ORDER_NOT_FOUND);
        }

        // 2. 可发货条件：仅"已支付"状态可发货
        if (order.getOrderStatus() != Constants.ORDER_STATUS_PAID) {
            throw new BusinessException(ORDER_STATUS_ERROR);
        }

        // 3. CAS 翻转：WHERE 带 order_status=1，且只 SET 物流三列 + 状态。
        //    旧写法 updateById(order) 是"先读快照、整行覆盖、WHERE 仅主键"：
        //    并发退款（markRefunded 已把单翻转成已退款并回补库存）会被本请求覆盖回
        //    "已发货+已支付"——库存已回补却继续履约，资损 + 状态机破坏（审计 严重-2）
        int rows = orderMapper.markShipped(orderNo, shippingCompany, trackingNo);
        if (rows == 0) {
            // 竞态：状态已被并发的退款/取消请求翻转，本请求按"状态不对"失败
            throw new BusinessException(ORDER_STATUS_ERROR);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelOrder(String orderNo) {
        Long userId = SecurityUtils.getUserId();

        // 1. 校验订单存在 + 属于当前用户 + 未删除（防越权，三重条件）
        QueryWrapper<Order> orderWrapper = new QueryWrapper<Order>()
                .eq("order_no", orderNo)
                .eq("user_id", userId);
        Order order = orderMapper.selectOne(orderWrapper);
        if (order == null) {
            throw new BusinessException(ORDER_NOT_FOUND);
        }

        // 2. 校验订单状态允许取消：仅"待支付"可取消
        // 2. conditional status flip (CAS): only UNPAID can be cancelled; concurrent cancels -> only one wins
        int rows = orderMapper.cancelUnpaid(orderNo, userId);
        if (rows == 0) {
            throw new BusinessException(ORDER_STATUS_ERROR);
        }

        // 3. 回补库存 + 写流水（与超时自动取消共用）
        restoreStockAndLog(order);
    }

    /**
     * 超时自动取消（定时任务调用）：与手动取消共用 CAS + 回补逻辑。
     * cancelUnpaidById 与支付路径的 markPaidById 同以 order_status=0 为条件，竞态时恰有一方生效。
     * 接收扫描实体而非回查 orderId：扫描源不过滤 deleted，回查（MP 内置方法）会被
     * @TableLogic 追加 deleted=0，历史"已删除仍待支付"的脏单会因查不到而跳过回补，泄漏依旧。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelTimeout(Order order) {
        int rows = orderMapper.cancelUnpaidById(order.getId());
        if (rows == 0) {
            return;   // 已被支付/取消（竞态方抢先），无事可做
        }
        restoreStockAndLog(order);
    }

    /** 定时任务数据源：超时未支付订单（分批限量） */
    @Override
    public List<Order> getTimeoutUnpaid(LocalDateTime deadline, int limit) {
        return orderMapper.selectTimeoutUnpaid(deadline, limit);
    }

    /**
     * 取消/超时共用的库存回补 + 流水写入（H5：明细按 SKU 汇总 → 每个热点行只锁一次 →
     * 批量读回最终库存 → 一次 insertBatch 流水。旧版逐条 restore+回读+insert，
     * 一单 N 条明细 = 3N 条 SQL；取消/超时任务高峰期会拖到下一轮都跑不完）。
     */
    private void restoreStockAndLog(Order order) {
        List<OrderItem> orderItemList = orderItemMapper.selectList(
                new QueryWrapper<OrderItem>().eq("order_id", order.getId()));
        if (orderItemList.isEmpty()) {
            return;
        }
        // 同 SKU 多条明细合并数量；LinkedHashMap 保持明细出现顺序，回补顺序稳定
        Map<Long, Integer> qtyBySku = new LinkedHashMap<>();
        for (OrderItem item : orderItemList) {
            qtyBySku.merge(item.getSkuId(), item.getQuantity(), Integer::sum);
        }
        // CAS 回补：restoreStock 自带 deleted=0 与防负库存条件；
        // 影响 0 行（SKU 被删等）跳过且不写流水，与旧版口径一致
        Map<Long, Integer> restoredQty = new LinkedHashMap<>();
        for (Map.Entry<Long, Integer> entry : qtyBySku.entrySet()) {
            if (productSkuMapper.restoreStock(entry.getKey(), entry.getValue()) > 0) {
                restoredQty.put(entry.getKey(), entry.getValue());
            }
        }
        if (!restoredQty.isEmpty()) {
            // 批量读回最终库存（1 次 SELECT 替代 N 次），before/after 为推算值
            //（读回前若有并发扣减/回补，绝对值会有偏差——与秒杀批量路径同口径）
            Map<Long, ProductSku> freshMap = productSkuMapper.selectBatchIds(restoredQty.keySet())
                    .stream().collect(Collectors.toMap(ProductSku::getId, s -> s));
            LocalDateTime now = LocalDateTime.now();
            List<StockLog> stockLogs = new ArrayList<>(restoredQty.size());
            for (Map.Entry<Long, Integer> entry : restoredQty.entrySet()) {
                ProductSku fresh = freshMap.get(entry.getKey());
                if (fresh == null) {
                    continue;   // 理论不可达：刚回补成功就查不到了
                }
                StockLog stockLog = new StockLog();
                stockLog.setSkuId(entry.getKey());
                stockLog.setOrderId(order.getId());
                stockLog.setOrderNo(order.getOrderNo());
                stockLog.setChangeType(Constants.STOCK_CHANGE_CANCEL);
                stockLog.setChangeQty(entry.getValue());
                stockLog.setBeforeStock(fresh.getStock() - entry.getValue());
                stockLog.setAfterStock(fresh.getStock());
                stockLog.setCreatedAt(now);
                stockLogs.add(stockLog);
            }
            if (!stockLogs.isEmpty()) {
                stockLogMapper.insertBatch(stockLogs);
            }
        }
        // 秒杀订单还要把秒杀活动库存（DB + Redis）还回去：上面的循环只回补了 product_sku 的普通库存，
        // 不回补秒杀库存的话，一笔被取消的秒杀单会永久占掉一个秒杀名额（库存单向只减不增）。
        // 非秒杀单在组件内部的第一道闸门就返回了，普通订单零额外开销。
        seckillStockRestorer.restoreForSeckillOrder(order, orderItemList);
    }
    @Override
    public void confirmOrder(String orderNo) {
        Long userId = SecurityUtils.getUserId();

        // 1. 先查订单，校验存在 + 归属 + 未删除（更清晰，能区分"不存在"和"状态不对"）
        QueryWrapper<Order> wrapper = new QueryWrapper<Order>()
                .eq("order_no", orderNo)
                .eq("user_id", userId);
        Order order = orderMapper.selectOne(wrapper);
        if (order == null) {
            throw new BusinessException(ORDER_NOT_FOUND);
        }

        // 2. 校验状态：只有"待收货"才能确认收货
        if (order.getOrderStatus() != Constants.ORDER_STATUS_SHIPPED) {
            throw new BusinessException(ORDER_STATUS_ERROR);
        }

        // 3. CAS 翻转（理由同 sendOrder，审计 严重-2）：并发退款不能被整行覆盖回"已完成"
        int rows = orderMapper.markCompleted(orderNo, userId);
        if (rows == 0) {
            throw new BusinessException(ORDER_STATUS_ERROR);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteOrder(String orderNo) {
        Long userId = SecurityUtils.getUserId();
        // selectOne 自动追加 deleted=0（@TableLogic）：已删除订单在这里直接查不到
        Order order = orderMapper.selectOne(new QueryWrapper<Order>()
                .eq("order_no", orderNo)
                .eq("user_id", userId));
        if (order == null) {
            throw new BusinessException(ORDER_NOT_FOUND);
        }

        // 状态闸门（堵"订单消失但库存不还"的泄漏）：
        // - 待支付(0)：先 CAS 取消（抢到才删）→ 回补库存 → 再删。旧实现不校验状态直接删，
        //   待支付单被删后超时任务永远扫不到，扣掉的库存永久占用（少卖且无对账手段）
        // - 待发货(1)/已发货(2)：资金/物流在途，禁止删除（必须走退款/收货流程）
        // - 已完成(3)/已取消(4)/已退款(5)：终态，允许清理
        if (order.getOrderStatus() == Constants.ORDER_STATUS_UNPAID) {
            int rows = orderMapper.cancelUnpaid(orderNo, userId);
            if (rows == 0) {
                // 竞态：支付/取消已抢先翻转状态，本请求按"状态不对"失败
                throw new BusinessException(ORDER_STATUS_ERROR);
            }
            restoreStockAndLog(order);
        } else if (order.getOrderStatus() == Constants.ORDER_STATUS_PAID
                || order.getOrderStatus() == Constants.ORDER_STATUS_SHIPPED) {
            throw new BusinessException(ORDER_STATUS_ERROR);
        }

        // CAS 逻辑删除：只 SET deleted/updated_at 两列，并发删除只有一次生效
        if (orderMapper.markDeleted(order.getId()) == 0) {
            throw new BusinessException(ORDER_NOT_FOUND);
        }
    }
    /**
     * 订单状态 -> 文本
     */
    private String statusToText(Integer status) {
        switch (status == null ? -1 : status) {
            case 0:  return "待支付";
            case 1:  return "待发货";
            case 2:  return "待收货";
            case 3:  return "已完成";
            case 4:  return "已取消";
            default: return "未知";
        }
    }
}
