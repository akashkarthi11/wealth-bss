package com.wealth.bss.common.model.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

// One environment setting (table wealth_settings). A missing username or password means the deployed one is used.
@Data
@EqualsAndHashCode(callSuper = false)
public class WealthSettingEntity extends GenericEntity {

	private static final long serialVersionUID = 1L;

	private String settingKey;
	private String settingValue;
	private String username;
	private String passwordEnc;

	@Override
	public String tableName() {
		return "WEALTH_SETTINGS";
	}

	@Override
	public String primaryKey() {
		return "setting_key";
	}
}
