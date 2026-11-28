package com.wealth.bss.common.model.entity;

import java.sql.Timestamp;

import lombok.Data;
import lombok.EqualsAndHashCode;

// A phone linked to a user for reading bank SMS (table wealth_sms_devices). Only the SHA-256 of its token is kept.
@Data
@EqualsAndHashCode(callSuper = false)
public class WealthSmsDeviceEntity extends GenericEntity {

	private static final long serialVersionUID = 1L;

	private String id;
	private String userId;
	private String label;
	private String tokenHash;
	private Timestamp createdAt;
	private Timestamp lastSeenAt;
	private int lastRead;
	private int lastSaved;

	@Override
	public String tableName() {
		return "WEALTH_SMS_DEVICES";
	}

	@Override
	public String primaryKey() {
		return "id";
	}
}
