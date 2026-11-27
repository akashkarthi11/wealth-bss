package com.wealth.bss.common.model.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

// The one key for everyone (table wealth_common_connections), kept encrypted; keyHint is its last characters, to recognise it.
@Data
@EqualsAndHashCode(callSuper = false)
public class WealthCommonConnectionEntity extends GenericEntity {

	private static final long serialVersionUID = 1L;

	private String provider;
	private String credentialEnc;
	private String keyHint;

	@Override
	public String tableName() {
		return "WEALTH_COMMON_CONNECTIONS";
	}

	@Override
	public String primaryKey() {
		return "provider";
	}
}
