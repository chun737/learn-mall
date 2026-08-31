package com.mall.mapper;

import com.mall.entity.SeckillActivity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mall.vo.SeckillActivityVO;
import com.mall.vo.SeckillAdminVO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * <p>
 * 秒杀活动表 Mapper 接口
 * </p>
 *
 * @author 乐乐
 * @since 2026-08-27
 */
public interface SeckillActivityMapper extends BaseMapper<SeckillActivity> {

    /**
     * 秒杀活动列表（联查 product / product_sku 组装 VO），
     * 按时间窗口过滤 + 动态状态筛选，配合 PageHelper 分页。
     *
     * @param status 活动状态筛选：0=未开始 1=进行中 2=已结束；null 查全部
     * @return 秒杀活动 VO 列表（不含库存实时值，库存由 Service 层从 Redis 读取）
     */
    List<SeckillActivityVO> selectSeckillList(@Param("status") Integer status);

    SeckillActivityVO selectDetailById(Long id);

    /**
     * 布隆过滤器预热数据源：全部未删除活动 ID（SeckillBloomIndex.loadAllIds 调用）
     */
    List<Long> selectAllIds();

    List<SeckillAdminVO> selectAdminList();

    /**
     * 4.7.7 终止活动：status 置 2（仅未删除的活动），返回影响行数（0 = 活动不存在或已删除）
     */
    int stopSeckillById(Long id);
}
