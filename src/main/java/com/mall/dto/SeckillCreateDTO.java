package com.mall.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 创建秒杀活动请求（后台 4.7.6）
 *
 * 格式类约束用声明式注解（缺参/非正数在进方法前被打回 400 + 字段提示）；
 * 依赖运行时上下文或跨字段的业务规则（须小于原价、时段先后、SKU 上架）仍在 Service 内校验。
 *
 * @author 乐乐
 */
@Data
public class SeckillCreateDTO {

    /** 活动名称 */
    @NotBlank(message = "活动名称不能为空")
    private String activityName;

    /** 秒杀 SKU ID（须存在且上架） */
    @NotNull(message = "秒杀 SKU ID 不能为空")
    private Long skuId;

    /** 秒杀价，须小于该 SKU 当前售价 */
    @NotNull(message = "秒杀价不能为空")
    @Positive(message = "秒杀价必须大于 0")
    private BigDecimal seckillPrice;

    /** 秒杀库存，> 0 */
    @NotNull(message = "秒杀库存不能为空")
    @Positive(message = "秒杀库存必须大于 0")
    private Integer totalStock;

    /** 每人限购数量，默认 1（可缺省，服务端兜底） */
    @Positive(message = "每人限购数量必须大于 0")
    private Integer perLimit;

    /** 开始时间（须晚于当前时间） */
    @NotNull(message = "开始时间不能为空")
    private LocalDateTime startTime;

    /** 结束时间（须晚于开始时间） */
    @NotNull(message = "结束时间不能为空")
    private LocalDateTime endTime;
}
