package com.wealth.bss.security;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class WealthAuthRateLimiter {
    public record Decision(boolean allowed, long retryAfterSeconds) {}
    private record Counter(long count, long expiresAt) {}

    private final StringRedisTemplate redis;
    private final ConcurrentHashMap<String, Counter> local = new ConcurrentHashMap<>();

    public WealthAuthRateLimiter(ObjectProvider<StringRedisTemplate> redisProvider) {
        this.redis = redisProvider.getIfAvailable();
    }

    public long count(String key) {
        if (redis != null) {
            try {
                String value = redis.opsForValue().get(redisKey(key));
                return value == null ? 0 : Long.parseLong(value);
            } catch (Exception ignored) { }
        }
        Counter c = local.get(key);
        if (c == null || c.expiresAt() <= System.currentTimeMillis()) {
            local.remove(key);
            return 0;
        }
        return c.count();
    }

    public Decision failedAttempt(String key, long limit, Duration window) {
        if (redis != null) {
            try {
                String k = redisKey(key);
                Long n = redis.opsForValue().increment(k);
                if (n != null && n == 1L) redis.expire(k, window);
                if (n != null && n > limit) {
                    Long ttl = redis.getExpire(k);
                    return new Decision(false, ttl != null && ttl > 0 ? ttl : window.toSeconds());
                }
                return new Decision(true, 0);
            } catch (Exception ignored) { }
        }
        long now = System.currentTimeMillis();
        Counter next = local.compute(key, (k, old) -> old == null || old.expiresAt() <= now
            ? new Counter(1, now + window.toMillis())
            : new Counter(old.count() + 1, old.expiresAt()));
        if (local.size() > 10000) local.entrySet().removeIf(e -> e.getValue().expiresAt() <= now);
        return new Decision(next.count() <= limit, Math.max(1, (next.expiresAt() - now) / 1000));
    }

    public void reset(String key) {
        if (redis != null) {
            try { redis.delete(redisKey(key)); } catch (Exception ignored) { }
        }
        local.remove(key);
    }

    private String redisKey(String key) { return "wealth:rl:" + key; }
}
