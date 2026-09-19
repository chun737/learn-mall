package com.mall.service.impl;

import com.mall.common.BusinessException;
import com.mall.common.PageUtils;
import com.mall.common.Constants;
import com.mall.dto.CouponCreateDTO;
import com.mall.entity.Coupon;
import com.mall.entity.UserCoupon;
import com.mall.mapper.CouponMapper;
import com.mall.mapper.UserCouponMapper;
import com.mall.service.ICouponService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mall.util.SecurityUtils;
import com.mall.vo.CouponAdminVO;
import com.mall.vo.CouponReceiveVO;
import com.mall.vo.CouponRecordVO;
import com.mall.vo.CouponVO;
import com.mall.vo.PageResult;
import com.github.pagehelper.PageInfo;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static com.mall.enums.ErrorCode.COUPON_LIMIT_REACHED;
import static com.mall.enums.ErrorCode.COUPON_RECEIVE_NOT_IN_TIME;
import static com.mall.enums.ErrorCode.COUPON_SOLD_OUT;
import static com.mall.enums.ErrorCode.NOT_FOUND;
import static com.mall.enums.ErrorCode.PARAM_ERROR;

/**
 * <p>
 * 优惠券模板表 服务实现类
 * </p>
 * 数据库访问全部走 CouponMapper.xml / UserCouponMapper.xml 中的 SQL（项目规范：CRUD 只用 SQL 语句）
 *
 * @author 乐乐
 * @since 2026-08-27
 */
@Service
public class CouponServiceImpl extends ServiceImpl<CouponMapper, Coupon> implements ICouponService {
    private final CouponMapper couponMapper;
    private final UserCouponMapper userCouponMapper;
    private final StringRedisTemplate stringRedisTemplate;

    /** Jackson 2 需显式注册 JavaTimeModule 才能序列化 LocalDateTime（Jackson 3 内置，2 不内置） */
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    /** 可领券列表缓存的 key/TTL 定义在 Constants（CACHE_KEY_COUPON_RECEIVABLE / TTL_COUPON_RECEIVABLE） */

    public CouponServiceImpl(CouponMapper couponMapper,
                             UserCouponMapper userCouponMapper,
                             StringRedisTemplate stringRedisTemplate) {
        this.couponMapper = couponMapper;
        this.userCouponMapper = userCouponMapper;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public List<CouponVO> receiveCoupons() {
        // 1. 先读缓存；Redis 故障时降级直查 DB（缓存不可用不阻断业务）
        String cached = null;
        try {
            cached = stringRedisTemplate.opsForValue().get(Constants.CACHE_KEY_COUPON_RECEIVABLE);
        } catch (Exception ignored) {
        }
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, new TypeReference<List<CouponVO>>() {
                });
            } catch (Exception e) {
                // 反序列化失败（脏缓存/版本升级残留），删除缓存后降级走 DB 查询
                try {
                    stringRedisTemplate.delete(Constants.CACHE_KEY_COUPON_RECEIVABLE);
                } catch (Exception ignored) {
                }
            }
        }

        // 2. 缓存未命中：SQL 查启用 + 未删除 + 领取窗口已开始且未结束的券
        List<Coupon> coupons = couponMapper.selectReceivableList();

        // 3. 转 VO：typeText 由后端统一映射，前端不维护枚举（与项目其他状态字段约定一致）
        List<CouponVO> voList = new ArrayList<>(coupons.size());
        for (Coupon coupon : coupons) {
            CouponVO vo = new CouponVO();
            BeanUtils.copyProperties(coupon, vo);
            vo.setTypeText(couponTypeText(coupon.getType()));
            voList.add(vo);
        }

        // 4. 回填缓存：空列表也缓存（避免空结果反复回源），TTL 5 分钟内允许展示滞后
        try {
            stringRedisTemplate.opsForValue()
                    .set(Constants.CACHE_KEY_COUPON_RECEIVABLE, objectMapper.writeValueAsString(voList), Constants.TTL_COUPON_RECEIVABLE);
        } catch (Exception ignored) {
        }
        return voList;
    }

    /**
     * 领取优惠券（frontend-api-guide 3.8）。
     * 事务内五步：行锁读 → 领取窗口校验 → 限领校验 → 条件扣减 + 生成持券记录。
     *
     * 并发一致性设计（多实例部署同样成立，锁在 DB 数据行上，与应用服务器数量无关）：
     * - 第 1 步 FOR UPDATE 悲观行锁串行化同一券的全部领取请求，关闭"两个并发请求同时
     *   通过限领 COUNT 校验 → 双双扣减 → 超领"的 check-then-act 竞态窗口；
     * - 第 4 步 remain_count 条件扣减（WHERE remain_count > 0）是防超发的最终防线，
     *   UPDATE 走当前读（非 MVCC 快照），总能看到最新已提交值。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public CouponReceiveVO receiveCoupon(Long couponId) {
        Long userId = SecurityUtils.getUserId();

        // 1. 行锁读：锁定该券的行直至事务结束，后续同券请求在此排队
        //    （COUNT 快照建立于锁获取之后，能看到前一事务已提交的插入记录）
        Coupon coupon = couponMapper.selectByIdForUpdate(couponId);
        if (coupon == null || Constants.DELETED == coupon.getDeleted()) {
            throw new BusinessException(NOT_FOUND);
        }

        // 2. 校验启用且当前时间在领取窗口内（以服务器时间为准，不信任客户端时间）
        LocalDateTime now = LocalDateTime.now();
        if (coupon.getStatus() == null || coupon.getStatus() != 1
                || now.isBefore(coupon.getReceiveStartTime())
                || now.isAfter(coupon.getReceiveEndTime())) {
            throw new BusinessException(COUPON_RECEIVE_NOT_IN_TIME);
        }

        // 3. 限领校验：按领取张数计（含已使用/已过期）。行锁已保证此刻没有并发领到一半的事务
        long received = userCouponMapper.countReceived(userId, couponId);
        if (received >= coupon.getPerLimit()) {
            throw new BusinessException(COUPON_LIMIT_REACHED);
        }

        // 4. 条件扣减余量防超发：影响行数 0 = 余量为 0（已领完）。
        //    必须先扣余量再插持券记录，避免"先插后扣"在扣减失败时多出回滚前的脏数据
        if (couponMapper.decreaseRemain(couponId) == 0) {
            throw new BusinessException(COUPON_SOLD_OUT);
        }

        // 5. 生成持券记录：有效期自领取时间起 validDays 天
        LocalDateTime expireTime = now.plusDays(coupon.getValidDays());
        UserCoupon userCoupon = new UserCoupon();
        userCoupon.setCouponId(couponId);
        userCoupon.setUserId(userId);
        userCoupon.setCouponStatus(0);          // 0=未使用
        userCoupon.setReceiveTime(now);
        userCoupon.setExpireTime(expireTime);
        userCoupon.setCreatedAt(now);
        userCouponMapper.insert(userCoupon);

        // 注：不逐出可领券列表缓存。剩余量的展示滞后由 5 分钟 TTL 兜底，
        // 正确性由第 4 步条件扣减保证；抢券高峰逐出缓存反而会让所有请求回源打库
        CouponReceiveVO vo = new CouponReceiveVO();
        vo.setUserCouponId(userCoupon.getId());
        vo.setCouponId(couponId);
        vo.setCouponName(coupon.getCouponName());
        vo.setCouponStatus(0);
        vo.setExpireTime(expireTime);
        return vo;
    }

    // ---------- 后台（frontend-api-guide 4.5） ----------

    /** 优惠券列表（4.7.1）：分页 + 状态/名称筛选，领取量/核销量由 SQL 联查统计 */
    @Override
    public PageResult<CouponAdminVO> listAdmin(Integer pageNum, Integer pageSize,
                                               Integer status, String keyword) {
        PageUtils.startPage(pageNum, pageSize);
        List<CouponAdminVO> voList = couponMapper.selectAdminList(status, keyword);
        PageInfo<CouponAdminVO> pageInfo = new PageInfo<>(voList);
        // 文本字段后端统一映射，前端不维护枚举
        voList.forEach(vo -> {
            vo.setTypeText(couponTypeText(vo.getType()));
            vo.setStatusText(vo.getStatus() != null && vo.getStatus() == 1 ? "启用" : "停用");
        });
        return PageResult.of(pageInfo, voList);
    }

    /** 创建优惠券（4.7.2）：白名单校验后落库，remain_count = totalCount，并逐出可领券缓存 */
    @Override
    public CouponAdminVO create(CouponCreateDTO dto) {
        // 1. 参数校验（frontend-api-guide 4.5 注意事项：时间区间、门槛与抵扣关系、总量与有效期为正）
        if (dto == null || dto.getCouponName() == null || dto.getCouponName().isBlank()
                || dto.getDiscountAmount() == null
                || dto.getTotalCount() == null || dto.getTotalCount() <= 0
                || dto.getReceiveStartTime() == null || dto.getReceiveEndTime() == null
                || dto.getValidDays() == null || dto.getValidDays() <= 0
                || !dto.getReceiveEndTime().isAfter(dto.getReceiveStartTime())) {
            throw new BusinessException(PARAM_ERROR);
        }
        boolean thresholdCoupon = dto.getType() != null && dto.getType() == 1;
        if (dto.getType() == null || (dto.getType() != 1 && dto.getType() != 2)) {
            throw new BusinessException(PARAM_ERROR);   // 折扣券（type=3）为扩展，暂不开放
        }
        if (thresholdCoupon
                && (dto.getThresholdAmount() == null || dto.getThresholdAmount().signum() <= 0)) {
            throw new BusinessException(PARAM_ERROR);   // 满减券必须有正门槛
        }
        if (dto.getDiscountAmount().signum() <= 0
                || (thresholdCoupon && dto.getDiscountAmount().compareTo(dto.getThresholdAmount()) >= 0)) {
            throw new BusinessException(PARAM_ERROR);   // 抵扣须为正数且小于门槛
        }

        // 2. 落库：余量 = 总量，无门槛券门槛固定 0
        Coupon coupon = new Coupon();
        coupon.setCouponName(dto.getCouponName().trim());
        coupon.setType(dto.getType());
        coupon.setThresholdAmount(thresholdCoupon ? dto.getThresholdAmount() : BigDecimal.ZERO);
        coupon.setDiscountAmount(dto.getDiscountAmount());
        coupon.setTotalCount(dto.getTotalCount());
        coupon.setRemainCount(dto.getTotalCount());
        coupon.setPerLimit(dto.getPerLimit() == null || dto.getPerLimit() <= 0 ? 1 : dto.getPerLimit());
        coupon.setReceiveStartTime(dto.getReceiveStartTime());
        coupon.setReceiveEndTime(dto.getReceiveEndTime());
        coupon.setValidDays(dto.getValidDays());
        coupon.setStatus(1);
        coupon.setDeleted(Constants.NOT_DELETED);
        couponMapper.insert(coupon);

        // 3. 逐出可领券缓存，让新券立即对前台可见
        evictReceivableCache();

        CouponAdminVO vo = new CouponAdminVO();
        BeanUtils.copyProperties(coupon, vo);
        vo.setReceivedCount(0);
        vo.setUsedCount(0);
        vo.setTypeText(couponTypeText(vo.getType()));
        vo.setStatusText("启用");
        return vo;
    }

    /** 启用/停用（4.7.3）：停用后前台不可再领，已领取的券仍可使用 */
    @Override
    public String changeStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException(PARAM_ERROR);
        }
        if (couponMapper.updateStatus(id, status) == 0) {
            throw new BusinessException(NOT_FOUND);
        }
        evictReceivableCache();
        return status == 1 ? "优惠券已启用" : "优惠券已停用";
    }

    /** 领取记录（4.7.4）：分页返回某券的领取/核销明细（SQL 联用户名） */
    @Override
    public PageResult<CouponRecordVO> listRecords(Long couponId, Integer pageNum, Integer pageSize) {
        PageUtils.startPage(pageNum, pageSize);
        List<CouponRecordVO> voList = userCouponMapper.selectRecords(couponId);
        PageInfo<CouponRecordVO> pageInfo = new PageInfo<>(voList);
        voList.forEach(vo -> vo.setCouponStatusText(recordStatusText(vo.getCouponStatus())));
        return PageResult.of(pageInfo, voList);
    }

    /** 券类型文本映射（frontend-api-guide 六 coupon.type） */
    private String couponTypeText(Integer type) {
        if (type == null) {
            return "";
        }
        return switch (type) {
            case 1 -> "满减券";
            case 2 -> "无门槛券";
            case 3 -> "折扣券";
            default -> "";
        };
    }

    /** 领取记录状态文本（后台按 DB 状态展示，不做过期惰性判定） */
    private String recordStatusText(Integer couponStatus) {
        if (couponStatus == null) {
            return "";
        }
        return switch (couponStatus) {
            case 1 -> "已使用";
            case 2 -> "已过期";
            default -> "未使用";
        };
    }

    /** 逐出可领券列表缓存（创建/启停后调用）；Redis 故障不影响主流程 */
    private void evictReceivableCache() {
        try {
            stringRedisTemplate.delete(Constants.CACHE_KEY_COUPON_RECEIVABLE);
        } catch (Exception ignored) {
        }
    }
}
