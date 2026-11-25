package com.wealth.bss.common.model.entity;

import java.sql.Timestamp;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class WealthUserEntity extends GenericEntity {

	private static final long serialVersionUID = 1L;

	private String id;
	private String email;
	private String role;
	private Integer sessionVersion;
	private Timestamp createdAt;

	@Override
	public String tableName() {
		return "WEALTH_USERS";
	}

	@Override
	public String primaryKey() {
		return "id";
	}
}
