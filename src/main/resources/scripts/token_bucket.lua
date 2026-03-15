-- Token Bucket Rate Limiter
-- KEYS[1] = bucket key (stores "tokens:last_refill_epoch" as hash)
-- ARGV[1] = bucket capacity (max tokens)
-- ARGV[2] = refill rate (tokens per second)
-- ARGV[3] = current epoch seconds
-- ARGV[4] = TTL for the key (seconds)
--
-- Returns: {allowed (1/0), remaining_tokens, retry_after_ms}

local capacity = tonumber(ARGV[1])
local refill_rate = tonumber(ARGV[2])
local now = tonumber(ARGV[3])
local ttl = tonumber(ARGV[4])

local bucket = redis.call('HMGET', KEYS[1], 'tokens', 'last_refill')
local tokens = tonumber(bucket[1])
local last_refill = tonumber(bucket[2])

if tokens == nil then
    -- First request: initialize bucket at full capacity minus 1 (this request)
    tokens = capacity - 1
    redis.call('HMSET', KEYS[1], 'tokens', tokens, 'last_refill', now)
    redis.call('EXPIRE', KEYS[1], ttl)
    return {1, tokens, 0}
end

-- Refill tokens based on elapsed time
local elapsed = now - last_refill
local refill = elapsed * refill_rate
tokens = math.min(capacity, tokens + refill)

-- Try to consume one token
if tokens >= 1 then
    tokens = tokens - 1
    redis.call('HMSET', KEYS[1], 'tokens', tokens, 'last_refill', now)
    redis.call('EXPIRE', KEYS[1], ttl)
    return {1, math.floor(tokens), 0}
else
    -- Not enough tokens — calculate retry after
    local deficit = 1 - tokens
    local retry_after = math.ceil(deficit / refill_rate)
    redis.call('HSET', KEYS[1], 'last_refill', now)
    redis.call('EXPIRE', KEYS[1], ttl)
    return {0, 0, retry_after}
end
