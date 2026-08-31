package com.mall.controller.admin;

import com.mall.common.Result;
import com.mall.dto.AdminUserDTO;
import com.mall.dto.UserRoleDTO;
import com.mall.service.IUserService;
import com.mall.vo.AdminUserVO;
import com.mall.vo.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台用户管理控制器（管理员专用）
 * <p>挂载于 /admin/user，由 SecurityConfig 的 hasRole(ADMIN) 保护</p>
 *
 * @author 乐乐
 */
@RestController("AdminUserController")
@RequestMapping("/admin/user")
@Tag(name = "后台用户管理", description = "创建用户、分配/移除角色")
public class AdminUserController {

    private final IUserService userService;

    public AdminUserController(IUserService userService) {
        this.userService = userService;
    }

    /**
     * 分页查询用户列表（含角色）
     */
    @GetMapping
    @Operation(summary = "用户列表", description = "分页查询用户，可按用户名/昵称模糊搜索")
    public Result<PageResult<AdminUserVO>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String keyword) {
        PageResult<AdminUserVO> result = userService.listUsers(pageNum, pageSize, keyword);
        return Result.success(result);
    }

    /**
     * 创建用户（可同时指定多个角色）
     */
    @PostMapping("/register")
    @Operation(summary = "创建用户", description = "管理员创建用户，可绑定 USER/ADMIN 等角色")
    public Result<AdminUserVO> create(@RequestBody AdminUserDTO dto) {
        AdminUserVO vo = userService.createUser(dto);
        return Result.success(vo);
    }

    /**
     * 给用户分配角色
     */
    @PostMapping("/role")
    @Operation(summary = "分配角色", description = "给用户增加一个角色")
    public Result<?> assignRole(@RequestBody UserRoleDTO dto) {
        userService.assignRole(dto);
        return Result.success("角色分配成功");
    }

    /**
     * 移除用户角色
     */
    @DeleteMapping("/role")
    @Operation(summary = "移除角色", description = "移除用户的某个角色")
    public Result<?> removeRole(@RequestBody UserRoleDTO dto) {
        userService.removeRole(dto);
        return Result.success("角色移除成功");
    }
}
