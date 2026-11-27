package com.wealth.bss.common.model.dao;

import com.wealth.bss.common.model.entity.WealthCommonConnectionEntity;

public interface IfWealthCommonConnectionDao {

	// The key for the provider, or null when none is saved.
	WealthCommonConnectionEntity find(String provider);

	// Saves the key (replaces the saved one).
	WealthCommonConnectionEntity upsert(WealthCommonConnectionEntity entity);

	// Removes the key; false when there was none.
	boolean delete(String provider);
}
