package com.wealth.bss.common.nexus;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.wealth.common.logger.KarthiLogger;

// Ported from growx-nexus-service's RequestHeaderServiceImple + growx-nexus-cache-lib's RedisClient:
// same key-namespacing and TTL behaviour, but through wealth-bss's own StringRedisTemplate (already used by
// WealthAuthRateLimiter) instead of a second, Jedis-based Redis client stack. spring-boot-starter-data-redis
// is a hard dependency of wealth-bss, so StringRedisTemplate always exists - no @ConditionalOnBean needed
// (and it is unreliable here anyway: evaluated before Spring Boot's own Redis autoconfiguration runs).
@Service
public class NexusCacheServiceImpl implements NexusCacheService {

	private static final KarthiLogger LOGGER = new KarthiLogger(NexusCacheServiceImpl.class);

	private final StringRedisTemplate redis;
	private final String namespace;
	private final long ttlSeconds;

	public NexusCacheServiceImpl(
			StringRedisTemplate redis,
			@Value("${wealth.nexus-cache.namespace:local}") String namespace,
			@Value("${wealth.nexus-cache.ttl-seconds:90000}") long ttlSeconds) {
		this.redis = redis;
		this.namespace = namespace;
		this.ttlSeconds = ttlSeconds;
	}

	private String namespaced(String key) {
		return namespace + "-" + key;
	}

	@Override
	public String getCacheData(String key, String userCode, String appId) {
		return redis.opsForValue().get(namespaced(key));
	}

	@Override
	public String setCacheData(String key, String data, String userCode, String appId) {
		String fullKey = namespaced(key);
		redis.opsForValue().set(fullKey, data, Duration.ofSeconds(ttlSeconds));
		return fullKey;
	}

	@Override
	public boolean removeCacheData(String key, String userCode, String appId) {
		LOGGER.info("removeCacheData start");
		boolean removed = Boolean.TRUE.equals(redis.delete(namespaced(key)));
		LOGGER.info("removeCacheData end | removed=" + removed);
		return removed;
	}
}
