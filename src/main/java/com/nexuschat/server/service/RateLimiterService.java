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

    // ── Limits ─────────────────────────────────────────────────────────────
    private static final int  IP_MAX_ATTEMPTS       = 20;
    private static final int  EMAIL_MAX_ATTEMPTS    = 10;
    private static final int  COMBO_MAX_ATTEMPTS    = 5;
    private static final long WINDOW_SECONDS        = 15 * 60; // 15 minutes

    // ── Core method ────────────────────────────────────────────────────────
    public boolean isAllowed(String key, int maxAttempts, long windowSeconds) {
        String redisKey = "ratelimit:" + key;

        Long attempts = redisTemplate.opsForValue().increment(redisKey);

        if (attempts == null) {
            return true; // Redis error - fail open
        }

        // First attempt — set expiry window
        if (attempts == 1) {
            redisTemplate.expire(redisKey, windowSeconds, TimeUnit.SECONDS);
        }

        if (attempts > maxAttempts) {
            long ttl = Optional.ofNullable(
                    redisTemplate.getExpire(redisKey, TimeUnit.SECONDS)
            ).orElse(0L);
            log.warn("🚫 Rate limit exceeded for [{}] — {} attempts, {}s remaining",
                    key, attempts, ttl);
            return false;
        }

        return true;
    }

    // ── Login — check all 3 layers ─────────────────────────────────────────
    public RateLimitResult checkLoginRateLimit(String ip, String email) {

        // Layer 1 — per IP (20 attempts / 15min)
        if (!isAllowed("login:ip:" + ip, IP_MAX_ATTEMPTS, WINDOW_SECONDS)) {
            return RateLimitResult.blocked(
                    "Too many requests from your network. Try again in 15 minutes.",
                    getRemainingAttempts("login:ip:" + ip, IP_MAX_ATTEMPTS)
            );
        }

        // Layer 2 — per email (10 attempts / 15min)
        if (!isAllowed("login:email:" + email, EMAIL_MAX_ATTEMPTS, WINDOW_SECONDS)) {
            return RateLimitResult.blocked(
                    "Too many attempts for this account. Try again in 15 minutes.",
                    getRemainingAttempts("login:email:" + email, EMAIL_MAX_ATTEMPTS)
            );
        }

        // Layer 3 — per IP + email combo (5 attempts / 15min)
        if (!isAllowed("login:ip+email:" + ip + ":" + email, COMBO_MAX_ATTEMPTS, WINDOW_SECONDS)) {
            return RateLimitResult.blocked(
                    "Too many login attempts. Try again in 15 minutes.",
                    getRemainingAttempts("login:ip+email:" + ip + ":" + email, COMBO_MAX_ATTEMPTS)
            );
        }

        return RateLimitResult.allowed();
    }

    // ── Reset all 3 layers on successful login ─────────────────────────────
    public void resetLoginRateLimit(String ip, String email) {
        redisTemplate.delete("ratelimit:login:ip:" + ip);
        redisTemplate.delete("ratelimit:login:email:" + email);
        redisTemplate.delete("ratelimit:login:ip+email:" + ip + ":" + email);
        log.info("✅ Rate limit reset for ip={} email={}", ip, email);
    }

    // ── Register — IP only is enough ──────────────────────────────────────
    public boolean checkRegisterRateLimit(String ip) {
        return isAllowed("register:ip:" + ip, 10, WINDOW_SECONDS);
    }

    public void resetRegisterRateLimit(String ip) {
        redisTemplate.delete("ratelimit:register:ip:" + ip);
    }

    // ── Helpers ────────────────────────────────────────────────────────────
    public int getRemainingAttempts(String key, int maxAttempts) {
        Object val = redisTemplate.opsForValue().get("ratelimit:" + key);
        if (val == null) return maxAttempts;
        int used = Integer.parseInt(val.toString());
        return Math.max(0, maxAttempts - used);
    }

    // ── Result wrapper ─────────────────────────────────────────────────────
    public static class RateLimitResult {
        public final boolean allowed;
        public final String  message;
        public final int     remainingAttempts;

        private RateLimitResult(boolean allowed, String message, int remainingAttempts) {
            this.allowed           = allowed;
            this.message           = message;
            this.remainingAttempts = remainingAttempts;
        }

        public static RateLimitResult allowed() {
            return new RateLimitResult(true, null, -1);
        }

        public static RateLimitResult blocked(String message, int remaining) {
            return new RateLimitResult(false, message, remaining);
        }
    }
}