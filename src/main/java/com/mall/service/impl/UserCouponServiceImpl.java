package com.mall.service.impl;

import com.mall.common.PageUtils;
import com.mall.entity.UserCoupon;
import com.mall.mapper.UserCouponMapper;
import com.mall.service.IUserCouponService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mall.util.SecurityUtils;
import com.mall.vo.AvailableCouponVO;
import com.mall.vo.PageResult;
import com.mall.vo.UserCouponVO;
import com.github.pagehelper.PageInfo;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * <p>
 * 用户持券表 服务实现类
 * </p>
 * 数据库访问全部走 UserCouponMapper.xml 中的 SQL（项目规范：CRUD 只用 SQL 语句）
 *
 * @author 乐乐
 * @since 2026-08-27
 */
@Service
public class UserCouponServiceImpl extends ServiceImpl<UserCouponMapper, UserCoupon> implements IUserCouponService {

    @Override
    public PageResult<UserCouponVO> listMyCoupons(Integer status, Integer pageNum, Integer pageSize) {
        Long userId = SecurityUtils.getUserId();

        // 1. SQL 联券模板直接组装 VO（UserCouponMapper.xml#selectMyCoupons），PageHelper 分页
        PageUtils.startPage(pageNum, pageSize);
        List<UserCouponVO> voList = baseMapper.selectMyCoupons(userId, status);
        PageInfo<UserCouponVO> pageInfo = new PageInfo<>(voList);

        // 2. 惰性过期展示：DB 里仍为 0=未使用但已过 expire_time 的，展示层直接判为已过期
        //（正式方案可用定时任务批量回写 coupon_status=2，见 frontend-api-guide 3.2 注意事项）
        LocalDateTime now = LocalDateTime.now();
        for (UserCouponVO vo : voList) {
            vo.setCouponStatusText(statusText(vo.getCouponStatus(), vo.getExpireTime(), now));
        }
        return PageResult.of(pageInfo, voList);
    }

    @Override
    public List<AvailableCouponVO> listAvailable(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            return new ArrayList<>();
        }
        Long userId = SecurityUtils.getUserId();

        // 1. SQL 取本人未使用且未过期的券（联模板，UserCouponMapper.xml#selectUsable）
        List<UserCouponVO> usable = baseMapper.selectUsable(userId);

        // 2. 门槛过滤：无门槛券直接可用；满减券按结算金额比较；折扣券为扩展未开放
        List<AvailableCouponVO> result = new ArrayList<>();
        for (UserCouponVO uc : usable) {
            if (uc.getType() == null || uc.getDiscountAmount() == null) {
                continue;
            }
            if (uc.getType() == 1) {
                if (uc.getThresholdAmount() == null
                        || uc.getThresholdAmount().compareTo(amount) > 0) {
                    continue;   // 结算金额未达门槛
                }
            } else if (uc.getType() != 2) {
                continue;   // type=3 折扣券暂不开放
            }
            AvailableCouponVO vo = new AvailableCouponVO();
            vo.setUserCouponId(uc.getUserCouponId());
            vo.setCouponName(uc.getCouponName());
            vo.setType(uc.getType());
            vo.setThresholdAmount(uc.getThresholdAmount());
            vo.setDiscountAmount(uc.getDiscountAmount());
            vo.setExpireTime(uc.getExpireTime());
            result.add(vo);
        }

        // 3. 抵扣额大者优先，相同则过期时间晚者优先；列表首条标记最优（全列表仅一条）
        result.sort(Comparator.comparing(AvailableCouponVO::getDiscountAmount).reversed()
                .thenComparing(AvailableCouponVO::getExpireTime, Comparator.nullsLast(Comparator.reverseOrder())));
        if (!result.isEmpty()) {
            result.get(0).setBest(1);
        }
        return result;
    }

    /** 持券状态文本：DB 状态优先，未使用但已过期的惰性判为已过期 */
    private String statusText(Integer couponStatus, LocalDateTime expireTime, LocalDateTime now) {
        if (couponStatus != null && couponStatus == 1) {
            return "已使用";
        }
        if ((couponStatus != null && couponStatus == 2)
                || (expireTime != null && expireTime.isBefore(now))) {
            return "已过期";
        }
        return "未使用";
    }
}
