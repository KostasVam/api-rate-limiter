-- Sliding Window Counter Rate Limiter
-- Uses weighted average of current and previous window counters
-- to smooth out boundary spikes.
--
-- KEYS[1] = current window key
-- KEYS[2] = previous window key
-- ARGV[1] = window TTL in seconds (with buffer)
-- ARGV[2] = weight of previous window (0.0 - 1.0, as integer percentage 0-100)
--
-- Returns: {current_count, previous_count, ttl_remaining, weight_pct}

-- Increment current window
local current = redis.call('INCR', KEYS[1])
if current == 1 then
    redis.call('EXPIRE', KEYS[1], ARGV[1])
end

-- Read previous window (may not exist)
local previous = tonumber(redis.call('GET', KEYS[2]) or '0')

local ttl = redis.call('TTL', KEYS[1])
local weight = tonumber(ARGV[2])

return {current, previous, ttl, weight}
