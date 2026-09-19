package com.mall.controller.user;

import com.mall.common.Result;
import com.mall.service.ISeckillActivityService;
import com.mall.vo.PageResult;
import com.mall.vo.SeckillActivityVO;
import com.mall.vo.SeckillResultVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

@RestController("UserSeckillController")
@RequestMapping("/seckill")
@Tag(name = "秒杀活动",description = "用户端参与秒杀活动")
public class SeckkillController {
    private final ISeckillActivityService seckillActivityService;

    public SeckkillController(ISeckillActivityService seckillActivityService) {
        this.seckillActivityService = seckillActivityService;
    }

    @GetMapping()
    @Operation(summary = "秒杀活动列表")
    public Result<PageResult<SeckillActivityVO>> getSkLists(
            // status 不传=全部（frontend-api-guide 3.10），分页参数带默认值
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize
    ){
        PageResult<SeckillActivityVO> list = seckillActivityService.getSkLists(status,pageNum,pageSize);
        return Result.success(list);
    }
    @GetMapping("/{id}")
    @Operation(summary = "秒杀活动详情")
    public Result<SeckillActivityVO> getSKDetail(@PathVariable("id") Long id){
        SeckillActivityVO seckillResultVO = seckillActivityService.getSKDetail(id);
        return Result.success(seckillResultVO);
    }
    @PostMapping("/{id}/orders")
    public Result<SeckillResultVO> seckill(@PathVariable("id") Long id,
                                           @RequestParam("addressId") Long addressId,
                                            @RequestParam(value = "quantity",defaultValue = "1") Integer quantity,
                                           @RequestParam(value = "couponId",required = false) Long couponId) {
        SeckillResultVO seckillResultVO = seckillActivityService.seckill(id,addressId,quantity,couponId);
        return Result.success(seckillResultVO);
    }

    @GetMapping("/{id}/result")
    @Operation(summary = "秒杀结果查询", description = "前端轮询获取当前用户在该活动最近一次抢购的结果（3.6.8）")
    public Result<SeckillResultVO> getResult(@PathVariable("id") Long id) {
        return Result.success(seckillActivityService.getResult(id));
    }
}
