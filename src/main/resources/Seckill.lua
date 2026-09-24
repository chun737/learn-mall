---@diagnostic disable: undefined-global
--@diagnostic disable: undefined-global
--- 秒杀原子预扣脚本
--- KEYS[1] = seckill:{act:<id>}:stock        库存计数（string，须预先 SET totalStock）
--- KEYS[2] = seckill:{act:<id>}:bought       已购数量（hash，field=userId，value=已购件数）
--- KEYS[3] = mall:seckill:result:{act:<id>}  抢购结果（hash，field=userId）
--- 注：<id> 是活动 id 占位符 —— 真实 key 只含一层花括号，如 seckill:{act:13}:stock
--- ARGV[1] = userId
--- ARGV[2] = 本次购买数量
--- ARGV[3] = perLimit 每人限购数量
--- ARGV[4] = bought/result key 的 TTL（秒，由 Java 常量传入）
--- ARGV[5] = JSON 编码的排队状态值（由 Java 常量传入）
--- 返回：1=成功  -1=库存不足  -2=超限购  -3=用户参数非法  -4=服务端参数缺失
---
local stock = tonumber(redis.call("GET", KEYS[1]))
local buy = tonumber(ARGV[2])
local perLimit = tonumber(ARGV[3])
local keyTtl = tonumber(ARGV[4])
local queuingValue = ARGV[5]

-- 用户侧参数校验：数量必须为正整数，限购至少为 1，防止 DECRBY 负数刷库存
if buy == nil or buy <= 0 or perLimit == nil or perLimit <= 0 then
    return -3
end

-- 服务端侧参数校验：keyTtl / queuingValue 由 Java 常量传入，缺失属于服务端问题。
-- 单独返回 -4（Java 侧映射为 SYSTEM_ERROR），避免和"用户传错参数"共用 -3 而把排查方向带偏。
if keyTtl == nil or keyTtl <= 0 or queuingValue == nil or queuingValue == '' then
    return -4
end

-- 库存校验：key 不存在视为未初始化，一律拒绝（宁可误拒不可超卖）
if stock == nil or stock < buy then
    return -1
end

-- 限购校验：先查已购数量再扣减，被拒绝时不会产生需要回滚的副作用
local bought = tonumber(redis.call("HGET", KEYS[2], ARGV[1]))
if bought == nil then
    bought = 0
end
if bought + buy > perLimit then
    return -2
end

redis.call("DECRBY", KEYS[1], buy)
redis.call("HINCRBY", KEYS[2], ARGV[1], buy)
if redis.call("TTL", KEYS[2]) < 0 then
    redis.call("EXPIRE", KEYS[2], keyTtl)
end
redis.call("HSET", KEYS[3], ARGV[1], queuingValue)
if redis.call("TTL", KEYS[3]) < 0 then
    redis.call("EXPIRE", KEYS[3], keyTtl)
end
return 1
