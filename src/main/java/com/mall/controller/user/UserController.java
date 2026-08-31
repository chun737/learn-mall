package com.mall.controller.user;


import com.mall.common.Result;
import com.mall.dto.AddressDTO;
import com.mall.dto.ChangePasswordDTO;
import com.mall.dto.ForgetPasswordDTO;
import com.mall.dto.UpdateProfileDTO;
import com.mall.service.IUserCouponService;
import com.mall.service.IUserService;
import com.mall.service.impl.UserServiceImpl;
import com.mall.vo.AddressVO;
import com.mall.vo.PageResult;
import com.mall.vo.UserCouponVO;
import com.mall.vo.UserProfileVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * 用户账号表 前端控制器
 * </p>
 *
 * @author 乐乐
 * @since 2026-08-16
 */
@RestController("UseruserController")
@RequestMapping("/user")
@Tag(name = "用户管理" , description = "后台用户管理")
public class UserController {
    private final IUserService userService;
    private final IUserCouponService userCouponService;

    public UserController(UserServiceImpl userService, IUserCouponService userCouponService) {
        this.userService = userService;
        this.userCouponService = userCouponService;
    }

    /**
     * 我的优惠券（api_doc 3.6.3）：分页 + 状态筛选（0=未使用 1=已使用 2=已过期，不传=全部）
     */
    @GetMapping("/coupons")
    @Operation(summary = "我的优惠券")
    public Result<PageResult<UserCouponVO>> myCoupons(@RequestParam(required = false) Integer status,
                                                      @RequestParam(defaultValue = "1") Integer pageNum,
                                                      @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(userCouponService.listMyCoupons(status, pageNum, pageSize));
    }




    @GetMapping("/profile")
    @Operation(summary = "用户信息",description = "查询用户信息")
    public Result<UserProfileVO> getinformation(){
        UserProfileVO userProfileVO = userService.getProfile();
        return Result.success(userProfileVO);
    }
    @PutMapping("/profile")
    @Operation(summary = "修改个人信息",description = "修改")
    public Result<UserProfileVO> modifyProfile(@RequestBody UpdateProfileDTO userProfileVO){
        UserProfileVO userProfileVO1 = userService.modifyProfile(userProfileVO);
        return Result.success(userProfileVO1);
    }
    @PutMapping("/password")
    @Operation(summary ="修改密码", description = "密码走请求体，不出现在 URL 中")
    public Result modifyPd(@Valid @RequestBody ChangePasswordDTO dto){
        userService.modifyPd(dto.getOldPassword(), dto.getNewPassword());
        return Result.success("密码修改成功，请重新登录");
    }
    @PutMapping("/password/forget")
    @Operation(summary = "忘记密码")
    public Result forgetPassword(@Valid @RequestBody ForgetPasswordDTO dto){
        userService.forgetPassword(dto);
        return Result.success("密码重置成功，请重新登录");
    }
    @GetMapping("/addresses")
    @Operation(summary = "收货地址列表查询")
    public Result<List<AddressVO>> listAddresses(){
        ArrayList<AddressVO> addressVOArrayList = userService.listAddresses();
        return Result.success(addressVOArrayList);
    }
    @PostMapping("/addresses")
    @Operation(summary = "新增收货地址")
    public Result<AddressVO> addAddress(@RequestBody AddressDTO addressDTO){
        AddressVO addressVO =  userService.addAddress(addressDTO);
        return Result.success(addressVO);
    }
    @DeleteMapping("/addresses/{id}")
    @Operation(summary = "删除收货地址")
    public Result deleteAddress(@PathVariable Long id){
        userService.deleteAddress(id);
        return Result.success("请重新设置默认收货地址");
    }
    @PutMapping("/addresses/{id}")
    @Operation(summary = "修改收货地址")
    public Result modifyAddress(@RequestBody AddressDTO addressDTO,
                                @PathVariable Long id){
        userService.modifyAddress(addressDTO,id);
        return Result.success("地址修改成功");
    }
    @PutMapping("/addresses/{id}/default")
    @Operation(summary = "设为默认地址")
    public Result isdefaultById(@PathVariable Long id){
        userService.isdefaultById(id);
        return  Result.success("已设为默认地址");
    }
}
