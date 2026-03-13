package com.nexuschat.server.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimiterService {

    private final RedisTemplate<String, Object> redisTemplate;

    // Max 5 attempts per window
    private static final int MAX_ATTEMPTS = 5;
    // 15 minute window
    private static final long WINDOW_SECONDS = 15 * 60;

    /**
     * Returns true if the request is ALLOWED.
     * Returns false if the rate limit has been exceeded.
     * Key is typically "auth:ip:<ipAddress>"
     */
    public boolean isAllowed(String key) {
        String redisKey = "ratelimit:" + key;

        // Increment the counter
        Long attempts = redisTemplate.opsForValue().increment(redisKey);

        if (attempts == null) {
            return true; // Redis error - fail open
        }

        // First attempt — set the expiry window
        if (attempts == 1) {
            redisTemplate.expire(redisKey, WINDOW_SECONDS, TimeUnit.SECONDS);
        }

        if (attempts > MAX_ATTEMPTS) {
            long ttl = Optional.ofNullable(
                    redisTemplate.getExpire(redisKey, TimeUnit.SECONDS)
            ).orElse(0L);
            log.warn("🚫 Rate limit exceeded for key: {} — {} attempts, TTL: {}s",
                    key, attempts, ttl);
            return false;
        }

        return true;
    }

    /**
     * Call this on successful login to reset the counter.
     */
    public void resetLimit(String key) {
        redisTemplate.delete("ratelimit:" + key);
    }

    /**
     * Get remaining attempts for a key.
     */
    public int getRemainingAttempts(String key) {
        String redisKey = "ratelimit:" + key;
        Object val = redisTemplate.opsForValue().get(redisKey);
        if (val == null) return MAX_ATTEMPTS;
        int used = Integer.parseInt(val.toString());
        return Math.max(0, MAX_ATTEMPTS - used);
    }
}