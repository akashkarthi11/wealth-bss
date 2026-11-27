package com.wealth.bss.nexus.serviceimple;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.wealth.bss.constants.WealthServiceConstants;
import com.wealth.bss.nexus.service.IfNexusCacheService;
import com.wealth.bss.nexus.service.IfRequestHeaderService;
import com.wealth.common.logger.KarthiLogger;

// Key is namespaced and stored with a TTL, as in growx-nexus-service; the Redis is shared, so keep the wealth: prefix.
// The read, write and removal go through the nexus cache, which uses Redis or the internal cache (rules 10a).
@Service
public class RequestHeaderServiceImple implements IfRequestHeaderService {

	private static final KarthiLogger LOGGER = new KarthiLogger(RequestHeaderServiceImple.class);

	private final IfNexusCacheService nexusCache;
	private final String namespace;
	private final long ttlSeconds;

	public RequestHeaderServiceImple(
			IfNexusCacheService nexusCache,
			@Value("${wealth.nexus-cache.namespace:wealth:nexus}") String namespace,
			@Value("${wealth.nexus-cache.ttl-seconds:300}") long ttlSeconds) {
		this.nexusCache = nexusCache;
		this.namespace = namespace;
		this.ttlSeconds = ttlSeconds;
	}

	private String namespaced(String key) {
		return namespace + "-" + key;
	}

	@Override
	public String getCacheData(String key) {
		return nexusCache.get(namespaced(key));
	}

	@Override
	public String setCacheData(String key, String data) {
		String fullKey = namespaced(key);
		nexusCache.put(fullKey, data, Duration.ofSeconds(ttlSeconds));
		return fullKey;
	}

	@Override
	public boolean removeCacheData(String key) {
		String methodName = "removeCacheData";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		boolean removed = nexusCache.remove(namespaced(key));
		LOGGER.info(WealthServiceConstants.EXITING + methodName + " | removed=" + removed);
		return removed;
	}
}
