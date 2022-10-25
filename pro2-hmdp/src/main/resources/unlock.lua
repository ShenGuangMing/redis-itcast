-- KEY[1] key ARGV[1] value

-- 获取锁中的标示 get key
local threadId = redis.call('get', KEYS[1])
-- 比较是否一致
if(threadId == ARGV[1]) then
    -- 释放锁
    return redis.call('del', KEYS[1])

end
return 0
