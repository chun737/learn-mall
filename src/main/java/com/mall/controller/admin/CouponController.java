package com.mall.controller.admin;

import com.mall.common.Result;
import com.mall.dto.CouponCreateDTO;
import com.mall.service.ICouponService;
import com.mall.vo.CouponAdminVO;
import com.mall.vo.CouponRecordVO;
import com.mall.vo.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 优惠券后台管理（api_doc 4.7）：需 ADMIN 角色（SecurityConfig 对 /admin/** 统一拦截）
 *
 * @author 乐乐
 */
@RestController("AdminCouponController")
@RequestMapping("/admin/coupon")
@Tag(name = "优惠券后台管理", description = "券列表/创建/启停/领取记录")
public class CouponController {

    private final ICouponService couponService;

    public CouponController(ICouponService couponService) {
        this.couponService = couponService;
    }

    /** 4.7.1 优惠券列表：分页 + 状态/名称筛选 */
    @GetMapping
    @Operation(summary = "优惠券列表")
    public Result<PageResult<CouponAdminVO>> list(@RequestParam(defaultValue = "1") Integer pageNum,
                                                  @RequestParam(defaultValue = "10") Integer pageSize,
                                                  @RequestParam(required = false) Integer status,
                                                  @RequestParam(required = false) String keyword) {
        return Result.success(couponService.listAdmin(pageNum, pageSize, status, keyword));
    }

    /** 4.7.2 创建优惠券 */
    @PostMapping
    @Operation(summary = "创建优惠券")
    public Result<CouponAdminVO> create(@RequestBody CouponCreateDTO dto) {
        return Result.success(couponService.create(dto));
    }

    /** 4.7.3 启用/停用：@RequestParam 与 AdminProductController 的状态接口风格一致 */
    @PutMapping("/{id}/status")
    @Operation(summary = "优惠券启用/停用")
    public Result<String> changeStatus(@PathVariable Long id,
                                       @RequestParam Integer status) {
        return Result.success(couponService.changeStatus(id, status));
    }

    /** 4.7.4 领取记录：分页返回某券的领取/核销明细 */
    @GetMapping("/{id}/records")
    @Operation(summary = "优惠券领取记录")
    public Result<PageResult<CouponRecordVO>> records(@PathVariable Long id,
                                                      @RequestParam(defaultValue = "1") Integer pageNum,
                                                      @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(couponService.listRecords(id, pageNum, pageSize));
    }
}
