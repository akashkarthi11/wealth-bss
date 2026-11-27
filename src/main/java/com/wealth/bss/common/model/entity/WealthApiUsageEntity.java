package com.wealth.bss.common.model.entity;

import java.time.LocalDate;

import lombok.Data;
import lombok.EqualsAndHashCode;

// The calls of one API (internal) or one outside provider endpoint (external) on one day (table wealth_api_usage, saved in batches).
@Data
@EqualsAndHashCode(callSuper = false)
public class WealthApiUsageEntity extends GenericEntity {

	private static final long serialVersionUID = 1L;

	private LocalDate day;
	private String kind;
	private String name;
	// The caller (wealth_users.id); the all-zero id when there was none. In a users listing, the email goes with it.
	private String userId;
	private String email;
	private long calls;
	private long success;
	private long clientErrors;
	private long failures;
	private long totalMs;

	@Override
	public String tableName() {
		return "WEALTH_API_USAGE";
	}

	@Override
	public String primaryKey() {
		return "day";
	}
}
