package com.mall.service;

import com.mall.dto.CouponCreateDTO;
import com.mall.entity.Coupon;
import com.baomidou.mybatisplus.extension.service.IService;
import com.mall.vo.CouponAdminVO;
import com.mall.vo.CouponReceiveVO;
import com.mall.vo.CouponRecordVO;
import com.mall.vo.CouponVO;
import com.mall.vo.PageResult;

import java.util.List;

/**
 * <p>
 * 优惠券模板表 服务类
 * </p>
 *
 * @author 乐乐
 * @since 2026-08-27
 */
public interface ICouponService extends IService<Coupon> {

    List<CouponVO> receiveCoupons();

    /**
     * 领取优惠券（api_doc 3.6.2）：事务内完成限领校验、余量条件扣减（防超发）、生成持券记录。
     */
    CouponReceiveVO receiveCoupon(Long couponId);

    // ---------- 后台（api_doc 4.7） ----------

    /** 优惠券列表（4.7.1）：分页 + 状态/名称筛选，含领取量与核销量统计 */
    PageResult<CouponAdminVO> listAdmin(Integer pageNum, Integer pageSize, Integer status, String keyword);

    /** 创建优惠券（4.7.2）：参数校验后落库，remain_count = totalCount，并逐出可领券缓存 */
    CouponAdminVO create(CouponCreateDTO dto);

    /** 启用/停用（4.7.3）：停用后前台不可再领，已领取的券仍可使用；并逐出可领券缓存 */
    String changeStatus(Long id, Integer status);

    /** 领取记录（4.7.4）：分页返回某券的领取/核销明细（SQL 联用户名） */
    PageResult<CouponRecordVO> listRecords(Long couponId, Integer pageNum, Integer pageSize);
}
