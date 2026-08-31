--@diagnostic disable: undefined-global
--- 秒杀原子预扣脚本
--- KEYS[1] = seckill:stock:{id}    库存计数（string，须预先 SET totalStock）
--- KEYS[2] = seckill:bought:{id}   已购数量（hash，field=userId，value=已购件数）
--- ARGV[1] = userId
--- ARGV[2] = 本次购买数量
--- ARGV[3] = perLimit 每人限购数量
--- 返回：1=成功  -1=库存不足  -2=超限购  -3=参数非法
---
local stock = tonumber(redis.call("GET", KEYS[1]))
local buy = tonumber(ARGV[2])
local perLimit = tonumber(ARGV[3])

-- 参数校验：数量必须为正整数，限购至少为 1，防止 DECRBY 负数刷库存
if buy == nil or buy <= 0 or perLimit == nil or perLimit <= 0 then
    return -3
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
return 1
