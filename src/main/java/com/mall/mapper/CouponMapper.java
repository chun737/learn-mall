package com.mall.mapper;

import com.mall.entity.Coupon;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mall.vo.CouponAdminVO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * <p>
 * 优惠券模板表 Mapper 接口
 * </p>
 *
 * @author 乐乐
 * @since 2026-08-27
 */
public interface CouponMapper extends BaseMapper<Coupon> {

    /** 可领优惠券列表（CouponMapper.xml#selectReceivableList） */
    List<Coupon> selectReceivableList();

    /** 行锁读：领券事务内使用，串行化同一券的并发领取，关闭限领校验竞态窗口 */
    Coupon selectByIdForUpdate(@Param("id") Long id);

    /** 领券条件扣减余量（防超发），返回影响行数：1=扣减成功 0=已领完 */
    int decreaseRemain(@Param("id") Long id);

    /** 后台优惠券列表（含领取量/核销量统计），配合 PageHelper 分页 */
    List<CouponAdminVO> selectAdminList(@Param("status") Integer status,
                                        @Param("keyword") String keyword);

    /** 启用/停用，返回影响行数：0=券不存在 */
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);
}
