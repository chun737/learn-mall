package com.mall.controller.user;

import com.mall.common.Result;
import com.mall.service.ICouponService;
import com.mall.service.IUserCouponService;
import com.mall.vo.AvailableCouponVO;
import com.mall.vo.CouponReceiveVO;
import com.mall.vo.CouponVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/coupon")
@Tag(name = "优惠卷管理",description = "优惠卷秒杀")
public class CouponController {
    private final ICouponService couponService;
    private final IUserCouponService userCouponService;

    public CouponController(ICouponService couponService, IUserCouponService userCouponService) {
        this.couponService = couponService;
        this.userCouponService = userCouponService;
    }

    @GetMapping()
    @Operation(summary = "可领优惠卷列表")
    public Result<List<CouponVO>> receiveCoupons(){
           List<CouponVO> couponVOList = couponService.receiveCoupons();
            return Result.success(couponVOList);
    }

    /**
     * 领取优惠券（api_doc 3.6.2）：POST /coupon/{id}/receive，需登录，
     * 用户 ID 由 JWT 过滤器注入 SecurityContext，经 SecurityUtils 获取（不信任前端传参）
     */
    @PostMapping("/{id}/receive")
    @Operation(summary = "领取优惠券")
    public Result<CouponReceiveVO> receive(@PathVariable Long id) {
        return Result.success(couponService.receiveCoupon(id));
    }

    /**
     * 结算可用优惠券（api_doc 3.6.4）：按结算商品总金额过滤本人可用券，首条标记最优。
     * 文档路径为 GET /coupons/available，此处跟随控制器既有 /coupon 前缀
     */
    @GetMapping("/available")
    @Operation(summary = "结算可用优惠券")
    public Result<List<AvailableCouponVO>> available(@RequestParam BigDecimal amount) {
        return Result.success(userCouponService.listAvailable(amount));
    }
}
