package com.wealth.bss.common.model.entity;

import java.sql.Timestamp;

import lombok.Data;
import lombok.EqualsAndHashCode;

// One user's own key for one provider (table wealth_user_connections), kept encrypted. lastStatus is "ok" or "failed" from the last check.
@Data
@EqualsAndHashCode(callSuper = false)
public class WealthUserConnectionEntity extends GenericEntity {

	private static final long serialVersionUID = 1L;

	private String userId;
	private String provider;
	private String credentialEnc;
	private String keyHint;
	private boolean enabled;
	private String lastStatus;
	private Timestamp lastCheckedAt;

	@Override
	public String tableName() {
		return "WEALTH_USER_CONNECTIONS";
	}

	@Override
	public String primaryKey() {
		return "user_id";
	}
}
