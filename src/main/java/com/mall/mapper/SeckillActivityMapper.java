package com.mall.mapper;

import com.mall.entity.SeckillActivity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mall.vo.SeckillActivityVO;
import com.mall.vo.SeckillAdminVO;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
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

    /**
     * 秒杀库存条件扣减（CAS）：available_stock >= qty 才允许扣，判断与写入在同一条 UPDATE 内。
     * 由 MQ 消费者在订单主表插入成功后调用（DB 侧对账 + 兜底防超卖）。
     * 影响行数 0 = DB 库存不足或活动不存在/已删除——调用方只记 WARN，不改建单结果。
     *
     * @param id  活动 ID
     * @param qty 扣减数量（正数）
     */
    int deductAvailableStock(@Param("id") Long id, @Param("qty") int qty);

    /**
     * 秒杀库存回补（取消/超时/退款）：行内自增，并用 LEAST 封顶在 total_stock，
     * 保证「已售数量 = total_stock - available_stock」不会变成负数。
     *
     * @param id  活动 ID
     * @param qty 回补数量（正数）
     */
    int restoreAvailableStock(@Param("id") Long id, @Param("qty") int qty);

    /**
     * 按「SKU + 下单时刻」反查所属秒杀活动 ID（回补路径用：订单表没有活动 ID 列）。
     * addSeckill 已校验「同一 SKU 不允许与未结束活动时间重叠」，故落点至多命中一条；
     * 仍按 start_time DESC 取第一条做兜底，避免历史脏数据导致 TooManyResultsException。
     *
     * @param skuId     订单明细里的 SKU ID
     * @param orderTime 订单下单时刻，须落在活动的 [start_time, end_time] 闭区间内
     * @return 活动 ID；查不到返回 null（说明该下单单并非在活动期内产生，不构成秒杀单）
     */
    Long selectActivityIdBySkuAt(@Param("skuId") Long skuId, @Param("orderTime") LocalDateTime orderTime);
}
