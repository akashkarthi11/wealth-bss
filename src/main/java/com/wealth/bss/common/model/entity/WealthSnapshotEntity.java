package com.wealth.bss.common.model.entity;

import java.util.Date;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class WealthSnapshotEntity extends GenericEntity {

	private static final long serialVersionUID = 1L;

	private String id;
	private String userId;
	private String sourceKey;
	private String sourceName;
	private Date snapshotDate;
	// jsonb, read as text and parsed with Jackson by the service.
	private String payload;

	@Override
	public String tableName() {
		return "WEALTH_SNAPSHOTS";
	}

	@Override
	public String primaryKey() {
		return "id";
	}
}
