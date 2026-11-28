package com.wealth.bss.common.model.entity;

import java.sql.Timestamp;

import lombok.Data;
import lombok.EqualsAndHashCode;

// Where one user's background Gmail read is (the sync columns of wealth_user_connections). stale: the read has not moved for a few minutes.
@Data
@EqualsAndHashCode(callSuper = false)
public class WealthUserSyncEntity extends GenericEntity {

	private static final long serialVersionUID = 1L;

	private String syncKind;
	private String syncStage;
	private String syncNote;
	private String syncResult;
	private Timestamp syncStartedAt;
	private Timestamp syncUpdatedAt;
	private boolean stale;

	@Override
	public String tableName() {
		return "WEALTH_USER_CONNECTIONS";
	}

	@Override
	public String primaryKey() {
		return "user_id";
	}
}
