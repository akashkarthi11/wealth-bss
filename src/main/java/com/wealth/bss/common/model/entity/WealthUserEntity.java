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

	@Override
	public String tableName() {
		return "WEALTH_USERS";
	}

	@Override
	public String primaryKey() {
		return "id";
	}
}
