package com.wealth.bss.nexus.service;

import java.time.Duration;
import java.util.function.Supplier;

import com.fasterxml.jackson.core.type.TypeReference;

import com.wealth.common.model.dto.CachedValueDto;

// Heavy reads are not computed from the tables on every call (rules 10). The key is built here from the signed-in user's id, never from the request.
// Fresh (younger than the refresh time): returned as it is. Stale: returned now and a refresh is requested (Kafka, or inside the BSS when Kafka is off).
// Missing: the loader reads the database once, the value is stored and returned.
public interface IfNexusReadService {

	// The loader runs on a refresh as well, with the same user, so it must read the user from the request context like the rest of the service.
	<T> CachedValueDto<T> read(String name, Class<T> type, Supplier<T> loader);

	// The dashboard Refresh button: asks for a refresh at once (age ignored), at most once every few seconds per user. Returns the value as it is now (stale until the refresh lands).
	<T> CachedValueDto<T> hardRefresh(String name, Class<T> type, Supplier<T> loader);

	// After a user's own change or a broker fetch: drops that user's cached value so the next read is fresh.
	void evict(String name, String userId);

	// A copy of a read that is asked again and again, kept for ttl, with no refresh machinery: found by name, the signed-in user and a variant (for example the filters).
	// evict(name, userId) drops every variant of that user at once. Never fails a read: when the cache cannot answer, the loader runs.
	<T> T remember(String name, String variant, Duration ttl, TypeReference<T> type, Supplier<T> loader);

	// The same for a read that is the same for everyone (the account types) or for everyone of a role (variant = the role). Dropped by evictShared.
	<T> T rememberShared(String name, String variant, Duration ttl, TypeReference<T> type, Supplier<T> loader);

	void evictShared(String name, String variant);
}
