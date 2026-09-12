-- 秒杀下单 Lua：库存扣减 + 一人一单校验 原子执行
-- KEYS[1] 秒杀库存 key  seckill:stock:{voucherId}
-- KEYS[2] 一人一单 set    seckill:user:{voucherId}
-- ARGV[1] 用户 id
-- 返回：0=成功 1=库存不足/活动不存在 2=重复下单(每人限购一单)

if redis.call('sismember', KEYS[2], ARGV[1]) == 1 then
    return 2
end

local stock = tonumber(redis.call('get', KEYS[1]) or '-1')
if stock <= 0 then
    return 1
end

redis.call('decr', KEYS[1])
redis.call('sadd', KEYS[2], ARGV[1])
return 0
