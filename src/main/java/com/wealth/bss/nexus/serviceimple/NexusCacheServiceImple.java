package com.wealth.bss.nexus.serviceimple;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.wealth.bss.config.RedisEndpoint;
import com.wealth.bss.constants.WealthServiceConstants;
import com.wealth.bss.nexus.service.IfNexusCacheService;
import com.wealth.common.logger.KarthiLogger;

// Redis first. When Redis is Inactive or fails, the internal cache (in memory, this instance only, size capped, with a time to live) does the same job.
// After a failure Redis is left alone for a few seconds, so a down Redis does not slow every request. The fallback is logged once when it starts.
@Service
public class NexusCacheServiceImple implements IfNexusCacheService {

	private static final KarthiLogger LOGGER = new KarthiLogger(NexusCacheServiceImple.class);

	private record Entry(String value, long expiresAt) {
	}

	private final StringRedisTemplate redis;
	private final RedisEndpoint endpoint;
	private final LongSupplier clock;
	private final ConcurrentHashMap<String, Entry> local = new ConcurrentHashMap<>();
	private volatile boolean degraded;
	private volatile long redisRetryAt;

	@Autowired
	public NexusCacheServiceImple(StringRedisTemplate redis, RedisEndpoint endpoint) {
		this(redis, endpoint, System::currentTimeMillis);
	}

	NexusCacheServiceImple(StringRedisTemplate redis, RedisEndpoint endpoint, LongSupplier clock) {
		this.redis = redis;
		this.endpoint = endpoint;
		this.clock = clock;
	}

	@Override
	public String get(String key) {
		if (useRedis()) {
			try {
				String value = redis.opsForValue().get(key);
				recovered();
				return value;
			} catch (RuntimeException e) {
				redisFailed(e);
			}
		}
		return localGet(key);
	}

	@Override
	public void put(String key, String value, Duration ttl) {
		if (useRedis()) {
			try {
				redis.opsForValue().set(key, value, ttl);
				recovered();
				return;
			} catch (RuntimeException e) {
				redisFailed(e);
			}
		}
		makeRoom(key);
		local.put(key, new Entry(value, clock.getAsLong() + ttl.toMillis()));
	}

	// Clears both, so an entry kept in the internal cache during an outage cannot outlive a role change, logout or password reset.
	@Override
	public boolean remove(String key) {
		Entry removedLocal = local.remove(key);
		boolean removed = removedLocal != null && removedLocal.expiresAt() > clock.getAsLong();
		if (useRedis()) {
			try {
				removed = Boolean.TRUE.equals(redis.delete(key)) || removed;
				recovered();
			} catch (RuntimeException e) {
				redisFailed(e);
			}
		}
		return removed;
	}

	@Override
	public long increment(String key, Duration window) {
		if (useRedis()) {
			try {
				Long n = redis.opsForValue().increment(key);
				if (n != null && n == 1L) {
					redis.expire(key, window);
				}
				recovered();
				return n == null ? 0 : n;
			} catch (RuntimeException e) {
				redisFailed(e);
			}
		}
		makeRoom(key);
		long now = clock.getAsLong();
		return Long.parseLong(local.compute(key, (k, old) -> old == null || old.expiresAt() <= now ? new Entry("1", now + window.toMillis())
				: new Entry(String.valueOf(Long.parseLong(old.value()) + 1), old.expiresAt())).value());
	}

	@Override
	public long count(String key) {
		String value = get(key);
		return value == null ? 0 : Long.parseLong(value);
	}

	@Override
	public boolean tryLock(String key, Duration ttl) {
		if (useRedis()) {
			try {
				boolean taken = Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, "1", ttl));
				recovered();
				return taken;
			} catch (RuntimeException e) {
				redisFailed(e);
			}
		}
		makeRoom(key);
		long now = clock.getAsLong();
		boolean[] taken = { false };
		local.compute(key, (k, old) -> {
			if (old == null || old.expiresAt() <= now) {
				taken[0] = true;
				return new Entry("1", now + ttl.toMillis());
			}
			return old;
		});
		return taken[0];
	}

	private boolean useRedis() {
		return endpoint.isEnabled() && clock.getAsLong() >= redisRetryAt;
	}

	private void recovered() {
		degraded = false;
	}

	private void redisFailed(RuntimeException e) {
		redisRetryAt = clock.getAsLong() + WealthServiceConstants.REDIS_RETRY_MILLIS;
		if (!degraded) {
			degraded = true;
			LOGGER.warn("Redis is not answering (" + e.getClass().getSimpleName() + "); the internal cache is used until it answers again");
		}
	}

	private String localGet(String key) {
		Entry entry = local.get(key);
		if (entry == null) {
			return null;
		}
		if (entry.expiresAt() <= clock.getAsLong()) {
			local.remove(key, entry);
			return null;
		}
		return entry.value();
	}

	// Bounded: expired entries are dropped first, and at the cap the entry expiring soonest goes.
	private void makeRoom(String key) {
		if (local.size() < WealthServiceConstants.NEXUS_LOCAL_MAX_ENTRIES || local.containsKey(key)) {
			return;
		}
		long now = clock.getAsLong();
		local.values().removeIf(entry -> entry.expiresAt() <= now);
		if (local.size() >= WealthServiceConstants.NEXUS_LOCAL_MAX_ENTRIES) {
			local.entrySet().stream().min((a, b) -> Long.compare(a.getValue().expiresAt(), b.getValue().expiresAt())).ifPresent(oldest -> local.remove(oldest.getKey()));
		}
	}
}
