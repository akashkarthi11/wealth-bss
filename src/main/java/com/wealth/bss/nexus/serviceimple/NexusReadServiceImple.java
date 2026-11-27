package com.wealth.bss.nexus.serviceimple;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wealth.bss.config.KafkaEndpoint;
import com.wealth.bss.constants.WealthServiceConstants;
import com.wealth.bss.nexus.service.IfNexusCacheService;
import com.wealth.bss.nexus.service.IfNexusEventService;
import com.wealth.bss.nexus.service.IfNexusReadService;
import com.wealth.common.logger.KarthiLogger;
import com.wealth.common.model.dto.CachedValueDto;
import com.wealth.common.utils.generic.RequestHeaderUtil;

@Service
public class NexusReadServiceImple implements IfNexusReadService {

	private static final KarthiLogger LOGGER = new KarthiLogger(NexusReadServiceImple.class);

	// What a refresh needs to run again: the type to store and the loader of the service that asked first.
	private record Loader(Class<?> type, Supplier<?> supplier) {
	}

	// The refresh request that travels through Kafka (or is handed over inside the BSS): which read, for whom, whether age is ignored, and when it was asked.
	record Refresh(String name, String userId, boolean forced, long requestedAt) {
	}

	private final IfNexusCacheService cache;
	private final IfNexusEventService events;
	private final KafkaEndpoint kafka;
	private final ObjectMapper mapper;
	private final LongSupplier clock;
	private final ExecutorService executor;
	private final ConcurrentHashMap<String, Loader> loaders = new ConcurrentHashMap<>();

	@Autowired
	public NexusReadServiceImple(IfNexusCacheService cache, IfNexusEventService events, KafkaEndpoint kafka, ObjectMapper mapper) {
		this(cache, events, kafka, mapper, System::currentTimeMillis, Executors.newFixedThreadPool(2, runnable -> {
			Thread thread = new Thread(runnable, "nexus-refresh");
			thread.setDaemon(true);
			return thread;
		}));
	}

	NexusReadServiceImple(IfNexusCacheService cache, IfNexusEventService events, KafkaEndpoint kafka, ObjectMapper mapper, LongSupplier clock, ExecutorService executor) {
		this.cache = cache;
		this.events = events;
		this.kafka = kafka;
		this.mapper = mapper;
		this.clock = clock;
		this.executor = executor;
		this.events.onRefresh(this::onRefreshMessage);
	}

	@Override
	public <T> CachedValueDto<T> read(String name, Class<T> type, Supplier<T> loader) {
		String userId = RequestHeaderUtil.getUserId();
		if (userId == null) {
			return fresh(loader.get());
		}
		loaders.put(name, new Loader(type, loader));
		String key = key(name, userId);
		CachedValueDto<T> cached = load(key, type);
		if (cached == null) {
			return fill(key, type, loader);
		}
		if (clock.getAsLong() - cached.getFetchedAt().getTime() < refreshMillis()) {
			return cached;
		}
		cached.setStale(true);
		request(new Refresh(name, userId, false, clock.getAsLong()), key);
		return cached;
	}

	@Override
	public <T> CachedValueDto<T> hardRefresh(String name, Class<T> type, Supplier<T> loader) {
		String userId = RequestHeaderUtil.getUserId();
		if (userId == null) {
			return fresh(loader.get());
		}
		loaders.put(name, new Loader(type, loader));
		String key = key(name, userId);
		CachedValueDto<T> cached = load(key, type);
		if (cached == null) {
			return fill(key, type, loader);
		}
		if (cache.tryLock(WealthServiceConstants.NEXUS_HARD_PREFIX + userId, Duration.ofSeconds(WealthServiceConstants.NEXUS_HARD_REFRESH_SECONDS))) {
			request(new Refresh(name, userId, true, clock.getAsLong()), key);
		}
		cached.setStale(true);
		return cached;
	}

	@Override
	public void evict(String name, String userId) {
		if (userId != null) {
			cache.remove(key(name, userId));
		}
	}

	private String key(String name, String userId) {
		return WealthServiceConstants.NEXUS_KEY_PREFIX + name + ":" + userId;
	}

	private long refreshMillis() {
		return kafka.refreshSeconds() * 1000L;
	}

	private <T> CachedValueDto<T> fresh(T data) {
		CachedValueDto<T> value = new CachedValueDto<>();
		value.setData(data);
		value.setFetchedAt(new Timestamp(clock.getAsLong()));
		return value;
	}

	// The first call for a key reads the database once and stores the result.
	private <T> CachedValueDto<T> fill(String key, Class<T> type, Supplier<T> loader) {
		CachedValueDto<T> value = fresh(loader.get());
		store(key, value);
		return value;
	}

	private <T> void store(String key, CachedValueDto<T> value) {
		try {
			cache.put(key, mapper.writeValueAsString(value), Duration.ofSeconds(WealthServiceConstants.NEXUS_READ_EXPIRY_SECONDS));
		} catch (com.fasterxml.jackson.core.JsonProcessingException e) {
			LOGGER.warn("A read could not be cached (" + e.getClass().getSimpleName() + ")");
		}
	}

	// A value that cannot be read back counts as missing.
	private <T> CachedValueDto<T> load(String key, Class<T> type) {
		String json = cache.get(key);
		if (json == null) {
			return null;
		}
		try {
			JavaType javaType = mapper.getTypeFactory().constructParametricType(CachedValueDto.class, type);
			CachedValueDto<T> value = mapper.readValue(json, javaType);
			return value == null || value.getData() == null || value.getFetchedAt() == null ? null : value;
		} catch (com.fasterxml.jackson.core.JsonProcessingException e) {
			LOGGER.warn("A cached read could not be read back (" + e.getClass().getSimpleName() + ")");
			return null;
		}
	}

	// One request per key at a time: a short lock is taken first. A forced request is already limited per user.
	private void request(Refresh refresh, String key) {
		if (!refresh.forced() && !cache.tryLock(WealthServiceConstants.NEXUS_LOCK_PREFIX + key, Duration.ofSeconds(WealthServiceConstants.NEXUS_LOCK_SECONDS))) {
			return;
		}
		executor.execute(() -> dispatch(refresh, key));
	}

	// Kafka when it is Active and takes the message; otherwise the BSS refreshes by itself (rules 10a).
	private void dispatch(Refresh refresh, String key) {
		try {
			if (kafka.isEnabled() && kafka.isConfigured() && events.publishRefresh(key, mapper.writeValueAsString(refresh))) {
				return;
			}
			run(refresh);
		} catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException e) {
			LOGGER.warn("A refresh request failed (" + e.getClass().getSimpleName() + "); the cached value stays and the next stale read asks again");
		}
	}

	private void onRefreshMessage(String payload) {
		try {
			run(mapper.readValue(payload, Refresh.class));
		} catch (com.fasterxml.jackson.core.JsonProcessingException e) {
			LOGGER.warn("A refresh request could not be read (" + e.getClass().getSimpleName() + ")");
		}
	}

	// Reads the database and writes the value. A request made before the stored value was read is skipped unless it is forced. A failure keeps the old value.
	void run(Refresh refresh) {
		Loader loader = loaders.get(refresh.name());
		if (loader == null || refresh.userId() == null) {
			return;
		}
		String key = key(refresh.name(), refresh.userId());
		if (!refresh.forced()) {
			CachedValueDto<?> current = load(key, loader.type());
			if (current != null && current.getFetchedAt().getTime() >= refresh.requestedAt()) {
				return;
			}
		}
		String previous = RequestHeaderUtil.getUserId();
		RequestHeaderUtil.setUserId(refresh.userId());
		try {
			store(key, fresh(loader.supplier().get()));
		} catch (RuntimeException e) {
			LOGGER.warn("A refresh could not read the database (" + e.getClass().getSimpleName() + "); the old value stays");
		} finally {
			RequestHeaderUtil.setUserId(previous);
		}
	}
}
