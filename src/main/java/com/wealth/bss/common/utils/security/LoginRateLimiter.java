package com.wealth.bss.common.utils.security;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.wealth.common.logger.KarthiLogger;

// Fixed-window counters in Redis, same keys as the Node app (wealth:rl:*). No local copy: if Redis fails the check is skipped, as in Node.
@Component
public class LoginRateLimiter {

	private static final KarthiLogger LOGGER = new KarthiLogger(LoginRateLimiter.class);
	private static final String PREFIX = "wealth:rl:";

	private final StringRedisTemplate redis;

	public LoginRateLimiter(StringRedisTemplate redis) {
		this.redis = redis;
	}

	public long count(String key) {
		try {
			String value = redis.opsForValue().get(PREFIX + key);
			return value == null ? 0 : Long.parseLong(value);
		} catch (RuntimeException e) {
			LOGGER.warn("Rate limit read failed: " + e.getClass().getSimpleName());
			return 0;
		}
	}

	// Counts one attempt; returns false once the limit is exceeded inside the window.
	public boolean attempt(String key, long limit, long windowSeconds) {
		try {
			String redisKey = PREFIX + key;
			Long n = redis.opsForValue().increment(redisKey);
			if (n != null && n == 1L) {
				redis.expire(redisKey, Duration.ofSeconds(windowSeconds));
			}
			return n == null || n <= limit;
		} catch (RuntimeException e) {
			LOGGER.warn("Rate limit write failed: " + e.getClass().getSimpleName());
			return true;
		}
	}

	public void reset(String key) {
		try {
			redis.delete(PREFIX + key);
		} catch (RuntimeException e) {
			LOGGER.warn("Rate limit reset failed: " + e.getClass().getSimpleName());
		}
	}
}
