-- Fixed Window Rate Limiter
-- KEYS[1] = rate limit key
-- ARGV[1] = window TTL in seconds (with buffer)
--
-- Returns: {current_count, ttl_remaining}

local current = redis.call('INCR', KEYS[1])
if current == 1 then
    redis.call('EXPIRE', KEYS[1], ARGV[1])
end
local ttl = redis.call('TTL', KEYS[1])
return {current, ttl}
