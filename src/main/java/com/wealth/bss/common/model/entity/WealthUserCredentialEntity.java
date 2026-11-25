package com.wealth.bss.common.model.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

// Only the login and password-reset queries read the hash; WealthUserEntity never carries it.
@Data
@EqualsAndHashCode(callSuper = false)
public class WealthUserCredentialEntity extends GenericEntity {

	private static final long serialVersionUID = 1L;

	private String id;
	private String email;
	private String passwordHash;
	private String role;
	private Integer sessionVersion;

	@Override
	public String tableName() {
		return "WEALTH_USERS";
	}

	@Override
	public String primaryKey() {
		return "id";
	}
}
