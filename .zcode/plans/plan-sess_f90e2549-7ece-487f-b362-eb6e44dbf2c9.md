## 目标
按 api_doc 3.6.5 实现秒杀活动列表接口，采用 **ZSet 索引分页**（score=开始时间毫秒戳、member=活动 ID），并顺带修复现有代码的 4 处问题。所有 DB 访问走 XML SQL（agent.md 规则 3），完成后按 agent.md 报告原因与行号。

## 现状（已探明）
- `SeckillActivityServiceImpl.getSkLists` 是未完成 stub：第 35 行悬空的 `String ` 语法错误、return null、注入了未使用的 `org.redisson.Redisson` 具体类
- `SeckillActivityMapper`/XML 为空壳；Controller 已存在（`SeckkillController`，GET /seckill，status 参数目前必填）
- `ErrorCode` 尚无 421xx 秒杀错误码；SecurityConfig 的 GET 白名单无 /seckill
- RedisTemplateConfig 已有 StringRedisTemplate 与 RedisTemplate<String,Object> 两个 Bean 可直接注入

## 实施步骤

### 1. SeckillActivityMapper.xml — 3 条 SQL
- `selectIndexAll`：`SELECT id, start_time FROM seckill_activity WHERE deleted = 0`（索引重建数据源）
- `selectVoByIds`：按 ID 集合水合 VO，JOIN product_sku + product 取商品名/主图/规格/SKU 原价（`s.price AS original_price`），`WHERE a.deleted = 0 AND a.id IN (...)`，resultType=SeckillActivityVO
- `selectVoList`：同上但不带 IN（Redis 故障时的降级全量查询）

### 2. SeckillActivityMapper.java — 对应方法声明

### 3. SeckillActivityServiceImpl 重写（核心）
- 修复 stub：删除悬空 `String`，移除 Redisson 具体类注入（未使用），改为注入 StringRedisTemplate（ZSet 索引用 `opsForZSet()`，member 存 ID 字符串、score 存开始时间戳）
- 常量：`INDEX_KEY = "mall:seckill:index"`、`STOCK_KEY_PREFIX = "seckill:stock:"`、索引 TTL 10 分钟
- `ensureIndex()`：key 不存在 → `selectIndexAll` 全量 ZADD 重建 + 设 TTL。TTL 的意义：4.7.6 创建活动（尚未实现）接入 ZADD 前，新活动最迟 10 分钟随重建可见（最终一致兜底）
- `getSkLists(status, pageNum, pageSize)` 主流程（整段包 try-catch，Redis 异常时降级走 `selectVoList` + 内存过滤分页）：
  1. ensureIndex
  2. status=null → `ZCARD` 取 total + `reverseRange(offset, offset+size-1)` 切页（核心演示路径）
  3. status=0（未开始）→ `reverseRangeByScore(Range(now 独占上界…+inf), Limit)` 区间精确切片，`zCount` 同区间取 total
  4. status=1/2（进行中/已结束）→ score 区间粗筛（start≤now 的全部候选 ID）→ 水合后按 end_time Java 精筛 → subList 分页（注释说明单 score 表达不了二维时间状态的原因）
  5. ID 水合：`selectVoByIds` 批量查，按 ZSet 返回顺序组装（防乱序）
  6. 每个 VO：`availableStock` 读 `seckill:stock:{id}`（秒杀预扣 key，尚未实现时回落 DB 值）；`activityStatus` 按 now 与 start/end 实时计算 + statusText 映射；`serverTime = now`
  7. 组装 PageResult（内存分页路径用 `PageResult.of(list, total, pageNum, pageSize)`）

### 4. SeckkillController 微调
- `status` 参数改 `required = false`（文档：不传=全部），类名拼写 `Seckkill` 保留不动（避免影响已有引用，报告里说明）

### 5. SecurityConfig
- GET permitAll 白名单加 `"/seckill/**"`（文档 3.6.5 标注【公开】）

### 6. mvn compile 验证 + agent.md 报告（原因+行号）

## 不做的事（边界）
- 不实现 4.7.6/4.7.7 后台管理（索引的 ZADD/ZREM 双写留待那时接入，本期以 TTL+懒重建兜底）
- 不实现游标深分页（作为注释/报告中的演进方向说明）
- 秒杀下单（3.6.7）不在本期范围