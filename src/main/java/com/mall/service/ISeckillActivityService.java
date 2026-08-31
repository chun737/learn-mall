package com.mall.service;

import com.mall.dto.SeckillCreateDTO;
import com.mall.entity.SeckillActivity;
import com.baomidou.mybatisplus.extension.service.IService;
import com.mall.vo.PageResult;
import com.mall.vo.SeckillActivityVO;
import com.mall.vo.SeckillAdminVO;
import com.mall.vo.SeckillResultVO;

/**
 * <p>
 * 秒杀活动表 服务类
 * </p>
 *
 * @author 乐乐
 * @since 2026-08-27
 */
public interface ISeckillActivityService extends IService<SeckillActivity> {

    PageResult<SeckillActivityVO> getSkLists(Integer status, Integer pageNum, Integer pageSize);

    SeckillActivityVO getSKDetail(Long id);

    SeckillResultVO seckill(Long id, Long addressId, Integer quantity, Long couponId);

    /**
     * 3.6.8 秒杀结果查询：当前用户在该活动最近一次抢购的结果
     */
    SeckillResultVO getResult(Long activityId);

    /**
     * 4.7.5 后台秒杀活动列表：分页返回全部活动（含未开始/已结束），status 不传=全部
     */
    PageResult<SeckillAdminVO> listSeckills(Integer status, Integer pageNum, Integer pageSize);

    /**
     * 4.7.6 创建秒杀活动：校验业务规则 + 落库 + Redis 预热，返回创建后的活动信息
     */
    SeckillAdminVO addSeckill(SeckillCreateDTO seckillCreateDTO);

    void stopSeckill(Long id);
}
