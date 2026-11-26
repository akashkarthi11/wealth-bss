package com.wealth.bss.common.model.dao;

import java.util.List;

import com.wealth.bss.common.model.entity.WealthSettingEntity;

// The environment settings an admin can change. The table is the source of truth; every application picks its settings from it when it boots.
public interface IfWealthSettingDao {

	// Every saved setting.
	List<WealthSettingEntity> findAll();

	// Creates or replaces the row of entity.settingKey (value, username, passwordEnc as given). Returns the saved row.
	WealthSettingEntity upsert(WealthSettingEntity entity);

	// Creates the row only when there is none yet; returns the row that exists afterwards.
	WealthSettingEntity insertIfMissing(WealthSettingEntity entity);
}
