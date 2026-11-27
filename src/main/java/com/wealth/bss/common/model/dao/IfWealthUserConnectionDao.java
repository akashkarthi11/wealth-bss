package com.wealth.bss.common.model.dao;

import java.util.List;

import com.wealth.bss.common.model.entity.WealthUserConnectionEntity;

public interface IfWealthUserConnectionDao {

	// The user's key for the provider, or null when none is saved.
	WealthUserConnectionEntity find(String userId, String provider);

	// Saves the user's key (replaces the saved one and clears the last check result).
	WealthUserConnectionEntity upsert(WealthUserConnectionEntity entity);

	// Changes only the encrypted value; the last check result is kept. Returns null when the user has no such key.
	WealthUserConnectionEntity saveCredential(String userId, String provider, String credentialEnc);

	// Saves the result of a check: status "ok" or "failed"; the time is now. Returns null when the user has no such key.
	WealthUserConnectionEntity saveCheck(String userId, String provider, String status);

	// Every user's enabled key for the provider, oldest first (for the daily fetch).
	List<WealthUserConnectionEntity> findByProvider(String provider);

	// Removes the user's key; false when there was none.
	boolean delete(String userId, String provider);
}
