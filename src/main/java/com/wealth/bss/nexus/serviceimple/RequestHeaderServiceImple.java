package com.wealth.bss.nexus.serviceimple;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.wealth.bss.constants.WealthServiceConstants;
import com.wealth.bss.nexus.service.IfRequestHeaderService;
import com.wealth.common.logger.KarthiLogger;

// Key is namespaced and stored with a TTL, as in growx-nexus-service; the Redis is shared, so keep the wealth: prefix.
@Service
public class RequestHeaderServiceImple implements IfRequestHeaderService {

	private static final KarthiLogger LOGGER = new KarthiLogger(RequestHeaderServiceImple.class);

	private final StringRedisTemplate redis;
	private final String namespace;
	private final long ttlSeconds;

	public RequestHeaderServiceImple(
			StringRedisTemplate redis,
			@Value("${wealth.nexus-cache.namespace:wealth:nexus}") String namespace,
			@Value("${wealth.nexus-cache.ttl-seconds:300}") long ttlSeconds) {
		this.redis = redis;
		this.namespace = namespace;
		this.ttlSeconds = ttlSeconds;
	}

	private String namespaced(String key) {
		return namespace + "-" + key;
	}

	@Override
	public String getCacheData(String key) {
		return redis.opsForValue().get(namespaced(key));
	}

	@Override
	public String setCacheData(String key, String data) {
		String fullKey = namespaced(key);
		redis.opsForValue().set(fullKey, data, Duration.ofSeconds(ttlSeconds));
		return fullKey;
	}

	@Override
	public boolean removeCacheData(String key) {
		String methodName = "removeCacheData";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		boolean removed = Boolean.TRUE.equals(redis.delete(namespaced(key)));
		LOGGER.info(WealthServiceConstants.EXITING + methodName + " | removed=" + removed);
		return removed;
	}
}
