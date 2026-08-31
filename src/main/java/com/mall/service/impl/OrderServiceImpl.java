package com.mall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.github.pagehelper.PageInfo;
import com.mall.common.BusinessException;
import com.mall.common.PageUtils;
import com.mall.common.Constants;
import com.mall.common.SnowflakeIdGenerator;
import com.mall.dto.AddressDTO;
import com.mall.dto.OrderCreateDTO;
import com.mall.dto.OrderSkuDTO;
import com.mall.entity.*;
import com.mall.mapper.CartMapper;
import com.mall.mapper.OrderItemMapper;
import com.mall.mapper.OrderMapper;
import com.mall.mapper.ProductMapper;
import com.mall.mapper.ProductSkuMapper;
import com.mall.mapper.StockLogMapper;
import com.mall.mapper.UserAddressMapper;
import com.mall.service.IOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mall.service.IUserService;
import com.mall.util.SecurityUtils;
import com.mall.vo.*;

import io.swagger.v3.oas.annotations.Operation;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.math.BigDecimal;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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
            SnowflakeIdGenerator idGenerator) {
        this.orderMapper = orderMapper;
        this.userService = userService;
        this.cartMapper =  cartMapper;
        this.productSkuMapper = productSkuMapper;
        this.userAddressMapper = userAddressMapper;
        this.orderItemMapper = orderItemMapper;
        this.stockLogMapper = stockLogMapper;
        this.productMapper = productMapper;
        this.idGenerator = idGenerator;
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

        // 1. 校验收货地址存在且属于当前用户（防越权）
        UserAddress address = userAddressMapper.selectById(orderCreateDTO.getAddressId());
        if (address == null || !address.getUserId().equals(userId)) {
            throw new BusinessException(NOT_FOUND);
        }

        // 2. 生成订单号
        String orderNo = generateOrderNo();
        List<OrderSkuDTO> skuList = orderCreateDTO.getSkuList();
        // 防御性校验：skuList 为空会导致下方遍历 NPE（Controller 层 @NotEmpty 已拦截，此处兜底）
        if (skuList == null || skuList.isEmpty()) {
            throw new BusinessException(PARAM_ERROR);
        }

        // 3. 先插入订单主表，拿到 order.id 供明细引用
        Order order = new Order();
        order.setOrderNo(orderNo)
                .setUserId(userId)
                .setTotalAmount(BigDecimal.ZERO)
                .setPayAmount(BigDecimal.ZERO)
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
        orderMapper.insert(order);

        // 4. 遍历商品：原子扣库存 + 写明细 + 写流水 + 累加金额
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (OrderSkuDTO item : skuList) {
            ProductSku sku = productSkuMapper.selectById(item.getSkuId());
            if (sku == null) {
                throw new BusinessException(STOCK_NOT_ENOUGH);
            }
            int quantity = item.getQuantity();

            // 4.1 原子扣减库存（CAS）：stock >= qty 才允许，并发下不会超卖
            int rows = productSkuMapper.deductStock(sku.getId(), quantity);
            if (rows == 0) {
                throw new BusinessException(STOCK_NOT_ENOUGH);
            }
            // 扣减成功后读一次最新值，流水对账用
            sku = productSkuMapper.selectById(sku.getId());
            int beforeStock = sku.getStock() + quantity;
            int afterStock = sku.getStock();

            // 4.2 查商品名（快照）
            Product product = productMapper.selectById(sku.getProductId());
            String productName = product != null ? product.getProductName() : "";

            // 4.3 写订单明细（带 orderId，无需二次回填）
            OrderItem orderItem = new OrderItem();
            orderItem.setOrderId(order.getId());
            orderItem.setOrderNo(orderNo);
            orderItem.setProductId(sku.getProductId());
            orderItem.setSkuId(sku.getId());
            orderItem.setProductName(productName);
            orderItem.setSkuSpecs(sku.getSpecs());
            orderItem.setSkuImage(sku.getImage());
            orderItem.setPrice(sku.getPrice());
            orderItem.setQuantity(quantity);
            orderItem.setTotalAmount(sku.getPrice().multiply(BigDecimal.valueOf(quantity)));
            orderItem.setCreatedAt(LocalDateTime.now());
            orderItem.setUpdatedAt(LocalDateTime.now());
            orderItemMapper.insert(orderItem);

            // 4.4 写库存流水
            StockLog stockLog = new StockLog();
            stockLog.setSkuId(sku.getId());
            stockLog.setOrderId(order.getId());
            stockLog.setOrderNo(orderNo);
            stockLog.setChangeType(Constants.STOCK_CHANGE_ORDER);
            stockLog.setChangeQty(-quantity);
            stockLog.setBeforeStock(beforeStock);
            stockLog.setAfterStock(afterStock);
            stockLog.setCreatedAt(LocalDateTime.now());
            stockLogMapper.insert(stockLog);

            // 4.5 累加金额
            totalAmount = totalAmount.add(sku.getPrice().multiply(BigDecimal.valueOf(quantity)));
        }

        // 5. 回填订单总金额
        order.setTotalAmount(totalAmount);
        order.setPayAmount(totalAmount);
        orderMapper.updateById(order);

        // 6. 清理购物车中已勾选的项
        cartMapper.delete(new QueryWrapper<com.mall.entity.Cart>()
                .eq("user_id", userId)
                .eq("checked", Constants.CHECKED));

        // 7. 组装响应
        OrderCreateVO orderCreateVO = new OrderCreateVO();
        orderCreateVO.setOrderNo(order.getOrderNo());
        orderCreateVO.setOrderId(order.getId());
        orderCreateVO.setOrderStatus(order.getOrderStatus());
        orderCreateVO.setPayAmount(order.getPayAmount());
        orderCreateVO.setCreatedAt(order.getCreatedAt());
        return orderCreateVO;
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
                        .eq("deleted", Constants.NOT_DELETED)
                        .last("limit 1"));
        if (order == null) {
            throw new BusinessException(ORDER_NOT_FOUND);
        }

        // 2. 可发货条件：仅"已支付"状态可发货
        if (order.getOrderStatus() != Constants.ORDER_STATUS_PAID) {
            throw new BusinessException(ORDER_STATUS_ERROR);
        }

        // 3. 更新订单为"已发货"：写物流信息 + 发货时间
        order.setOrderStatus(Constants.ORDER_STATUS_SHIPPED);
        order.setShippingCompany(shippingCompany);
        order.setTrackingNo(trackingNo);
        order.setShippedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        orderMapper.updateById(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelOrder(String orderNo) {
        Long userId = SecurityUtils.getUserId();

        // 1. 校验订单存在 + 属于当前用户 + 未删除（防越权，三重条件）
        QueryWrapper<Order> orderWrapper = new QueryWrapper<Order>()
                .eq("order_no", orderNo)
                .eq("user_id", userId)
                .eq("deleted", Constants.NOT_DELETED);
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
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelTimeout(Long orderId) {
        int rows = orderMapper.cancelUnpaidById(orderId);
        if (rows == 0) {
            return;   // 已被支付/取消（竞态方抢先），无事可做
        }
        Order order = orderMapper.selectById(orderId);
        if (order != null) {
            restoreStockAndLog(order);
        }
    }

    /** 定时任务数据源：超时未支付订单（分批限量） */
    @Override
    public List<Order> getTimeoutUnpaid(LocalDateTime deadline, int limit) {
        return orderMapper.selectTimeoutUnpaid(deadline, limit);
    }

    /** 取消/超时共用的库存回补 + 流水写入（销量回退兜底不为负） */
    private void restoreStockAndLog(Order order) {
        List<OrderItem> orderItemList = orderItemMapper.selectList(
                new QueryWrapper<OrderItem>().eq("order_id", order.getId()));
        for (OrderItem item : orderItemList) {
            int restored = productSkuMapper.restoreStock(item.getSkuId(), item.getQuantity());
            if (restored == 0) {
                continue;
            }
            ProductSku fresh = productSkuMapper.selectById(item.getSkuId());
            int afterStock = fresh.getStock();
            int beforeStock = afterStock - item.getQuantity();

            StockLog stockLog = new StockLog();
            stockLog.setSkuId(item.getSkuId());
            stockLog.setOrderId(order.getId());
            stockLog.setOrderNo(order.getOrderNo());
            stockLog.setChangeType(Constants.STOCK_CHANGE_CANCEL);
            stockLog.setChangeQty(item.getQuantity());
            stockLog.setBeforeStock(beforeStock);
            stockLog.setAfterStock(afterStock);
            stockLog.setCreatedAt(LocalDateTime.now());
            stockLogMapper.insert(stockLog);
        }
    }
    @Override
    public void confirmOrder(String orderNo) {
        Long userId = SecurityUtils.getUserId();

        // 1. 先查订单，校验存在 + 归属 + 未删除（更清晰，能区分"不存在"和"状态不对"）
        QueryWrapper<Order> wrapper = new QueryWrapper<Order>()
                .eq("order_no", orderNo)
                .eq("user_id", userId)
                .eq("deleted", Constants.NOT_DELETED);
        Order order = orderMapper.selectOne(wrapper);
        if (order == null) {
            throw new BusinessException(ORDER_NOT_FOUND);
        }

        // 2. 校验状态：只有"待收货"才能确认收货
        if (order.getOrderStatus() != Constants.ORDER_STATUS_SHIPPED) {
            throw new BusinessException(ORDER_STATUS_ERROR);
        }

        // 3. 更新状态为"已完成" + 记录完成时间
        order.setOrderStatus(Constants.ORDER_STATUS_COMPLETED);
        order.setCompletedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        orderMapper.updateById(order);
    }

    @Override
    public void deleteOrder(String orderNo) {
        Long userId = SecurityUtils.getUserId();
        QueryWrapper<Order> wrapper = new QueryWrapper<Order>()
                .eq("order_no", orderNo)
                .eq("user_id", userId);
        Order order = orderMapper.selectOne(wrapper);
        if (order == null || order.getDeleted() == Constants.DELETED) {
            throw new BusinessException(ORDER_NOT_FOUND);
        }
        order.setDeleted(Constants.DELETED);
        order.setUpdatedAt(LocalDateTime.now());
        orderMapper.updateById(order);
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
