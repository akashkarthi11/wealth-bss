package com.wealth.bss.nexus.service;

import java.util.function.Supplier;

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
}
