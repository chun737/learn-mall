package com.mall.controller.admin;

import com.mall.common.Result;
import com.mall.dto.SeckillCreateDTO;
import com.mall.service.ISeckillActivityService;
import com.mall.vo.PageResult;
import com.mall.vo.SeckillAdminVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController("AdminSeckillController")
@RequestMapping("/admin/seckill")
@Tag(name = "管理端秒杀管理",description = "秒杀时间管理")
public class SeckillController {
    private final ISeckillActivityService seckillActivityService;

    public SeckillController(ISeckillActivityService seckillActivityService) {
        this.seckillActivityService = seckillActivityService;
    }

    @GetMapping()
    @Operation(summary = "秒杀活动列表", description = "分页返回全部活动（含未开始/已结束），status 不传=全部（4.7.5）")
    public Result<PageResult<SeckillAdminVO>> listSeckills(
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        return Result.success(seckillActivityService.listSeckills(status, pageNum, pageSize));
    }
    @PostMapping()
    @Operation( summary = "添加秒杀活动", description = "校验业务规则后落库并预热 Redis，返回创建后的活动信息（4.7.6）")
    public Result<SeckillAdminVO> addSeckill(@Valid @RequestBody SeckillCreateDTO seckillCreateDTO){
        return Result.success(seckillActivityService.addSeckill(seckillCreateDTO));
    }
    @PutMapping("/{id}/terminate")
    @Operation(summary = "终止秒杀活动")
    public Result stopSeckill(@PathVariable("id") Long id){
        seckillActivityService.stopSeckill(id);
        return Result.success("活动结束");
    }
}
