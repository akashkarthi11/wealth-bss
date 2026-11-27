package com.wealth.bss.common.utils.security;

import java.time.Duration;

import org.springframework.stereotype.Component;

import com.wealth.bss.nexus.service.IfNexusCacheService;

// Fixed-window counters, same keys as the Node app (wealth:rl:*). They go through the nexus cache: Redis while it answers, the internal counters otherwise,
// so the limits still apply and are never dropped (rules 10 and 10a).
@Component
public class LoginRateLimiter {

	private static final String PREFIX = "wealth:rl:";

	private final IfNexusCacheService nexusCache;

	public LoginRateLimiter(IfNexusCacheService nexusCache) {
		this.nexusCache = nexusCache;
	}

	public long count(String key) {
		return nexusCache.count(PREFIX + key);
	}

	// Counts one attempt; returns false once the limit is exceeded inside the window.
	public boolean attempt(String key, long limit, long windowSeconds) {
		return nexusCache.increment(PREFIX + key, Duration.ofSeconds(windowSeconds)) <= limit;
	}

	public void reset(String key) {
		nexusCache.remove(PREFIX + key);
	}
}
