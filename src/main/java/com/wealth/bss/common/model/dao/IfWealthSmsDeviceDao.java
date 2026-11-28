package com.wealth.bss.common.model.dao;

import java.util.List;

import com.wealth.bss.common.model.entity.WealthSmsDeviceEntity;

public interface IfWealthSmsDeviceDao {

	// How many phones the user has linked and not removed.
	int countActive(String userId);

	// Links a phone: stores the hash of its token and returns the new id.
	String insert(String userId, String label, String tokenHash);

	// The user's linked phones, newest first (never the token hash).
	List<WealthSmsDeviceEntity> findActive(String userId);

	// Removes a phone: its token stops working at once. False when it is not the user's or is already removed.
	boolean revoke(String userId, String id);

	// The phone a token belongs to (id and userId only), or null when it is unknown or removed.
	WealthSmsDeviceEntity findByTokenHash(String tokenHash);

	// Notes that the phone just sent messages: when, how many were read and how many became transactions.
	void touch(String id, int read, int saved);
}
