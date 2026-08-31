package com.mall.service;

import com.mall.entity.UserCoupon;
import com.baomidou.mybatisplus.extension.service.IService;
import com.mall.vo.AvailableCouponVO;
import com.mall.vo.PageResult;
import com.mall.vo.UserCouponVO;

import java.math.BigDecimal;
import java.util.List;

/**
 * <p>
 * 用户持券表 服务类
 * </p>
 *
 * @author 乐乐
 * @since 2026-08-27
 */
public interface IUserCouponService extends IService<UserCoupon> {

    /**
     * 我的优惠券（api_doc 3.6.3）：分页 + 状态筛选。
     * 已过期未使用的券在展示层惰性判定为"已过期"（不回写 DB）。
     */
    PageResult<UserCouponVO> listMyCoupons(Integer status, Integer pageNum, Integer pageSize);

    /**
     * 结算可用优惠券（api_doc 3.6.4）：按结算金额过滤未使用、未过期、满足门槛的券，
     * 抵扣额大者优先（相同则过期时间晚者优先），列表首条标记 best=1。
     */
    List<AvailableCouponVO> listAvailable(BigDecimal amount);
}
