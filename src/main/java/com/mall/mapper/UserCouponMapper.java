package com.mall.mapper;

import com.mall.entity.UserCoupon;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mall.vo.CouponRecordVO;
import com.mall.vo.UserCouponVO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * <p>
 * 用户持券表 Mapper 接口
 * </p>
 *
 * @author 乐乐
 * @since 2026-08-27
 */
public interface UserCouponMapper extends BaseMapper<UserCoupon> {

    /** 限领校验：统计本人已领某券的张数（UserCouponMapper.xml#countReceived） */
    long countReceived(@Param("userId") Long userId, @Param("couponId") Long couponId);

    /** 我的优惠券（联券模板），配合 PageHelper 分页 */
    List<UserCouponVO> selectMyCoupons(@Param("userId") Long userId,
                                       @Param("status") Integer status);

    /** 结算可用券底表：未使用且未过期 */
    List<UserCouponVO> selectUsable(@Param("userId") Long userId);

    /** 后台领取记录（联用户名），配合 PageHelper 分页 */
    List<CouponRecordVO> selectRecords(@Param("couponId") Long couponId);

    /** 下单核销前校验读：本人 + 未使用 + 未过期的持券记录联模板（不存在/已用/过期返回 null） */
    UserCouponVO selectRedeemable(@Param("userCouponId") Long userCouponId,
                                  @Param("userId") Long userId);

    /** 核销（CAS）：仅未使用且未过期可核销，写入核销时间与订单号；0 = 已被并发核销或已过期 */
    int redeem(@Param("userCouponId") Long userCouponId,
               @Param("userId") Long userId,
               @Param("orderNo") String orderNo);
}
