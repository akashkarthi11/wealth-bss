package com.wealth.bss.common.model.dao;

import java.util.List;

import com.wealth.bss.common.model.entity.WealthConnectionEntity;

public interface IfWealthConnectionDao {

	// Every provider, by priority then name.
	List<WealthConnectionEntity> findAll();

	// Creates or changes the provider. A null credentialEnc or keyHint keeps what is saved; a change clears the last test result.
	WealthConnectionEntity upsert(WealthConnectionEntity entity);

	// Saves the result of a test: entity.provider and entity.lastStatus ("ok" or "failed"); the time is now. Returns null when there is no such provider.
	WealthConnectionEntity saveTestResult(WealthConnectionEntity entity);

	// Saves the result of a fetch: entity.provider and entity.lastFetchNote (short); the time is now. Returns null when there is no such provider.
	WealthConnectionEntity saveFetchResult(WealthConnectionEntity entity);

	// Empties the shared login (key, other login values and key hint) of the provider, keeping its address and switches; false when there was no such provider.
	boolean clearCredential(String provider);

	// Removes the provider; false when there was none.
	boolean delete(String provider);

	// The overview rows of one part: main, common or user. For user, userId limits it to that user; empty = every user. Never a key.
	List<com.wealth.common.model.dto.admin.ConnectionOverviewRowDto> overview(String part, String userId);
}
