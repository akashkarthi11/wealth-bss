package com.wealth.bss.common.model.entity;

import java.sql.Timestamp;

import lombok.Data;
import lombok.EqualsAndHashCode;

// One third-party service (table wealth_connections). The key is kept encrypted; keyHint is its last characters, to recognise it.
@Data
@EqualsAndHashCode(callSuper = false)
public class WealthConnectionEntity extends GenericEntity {

	private static final long serialVersionUID = 1L;

	private String provider;
	private String label;
	private String baseUrl;
	private String credentialEnc;
	private String keyHint;
	private boolean enabled;
	private int priority;
	private String lastStatus;
	private Timestamp lastCheckedAt;
	// The other login values as one encrypted block (Angel One: client code, PIN, TOTP secret, MAC address).
	private String credentialExtraEnc;
	// The user whose accounts fetched data is written to, the daily-fetch switch, and the last fetch.
	private String ownerUserId;
	private boolean autoFetch;
	private Timestamp lastFetchAt;
	private String lastFetchNote;

	@Override
	public String tableName() {
		return "WEALTH_CONNECTIONS";
	}

	@Override
	public String primaryKey() {
		return "provider";
	}
}
