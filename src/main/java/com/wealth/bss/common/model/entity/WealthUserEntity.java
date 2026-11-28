package com.wealth.bss.common.model.entity;


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
	// Profile details (My profile); null when not set.
	private String displayName;
	private String mobile;
	private String prefDateFormat;
	private String prefHome;

	@Override
	public String tableName() {
		return "WEALTH_USERS";
	}

	@Override
	public String primaryKey() {
		return "id";
	}
}
