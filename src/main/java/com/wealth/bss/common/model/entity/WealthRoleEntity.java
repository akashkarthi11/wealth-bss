package com.wealth.bss.common.model.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

// One role (table wealth_roles). A locked role (admin) always sees every menu and cannot be changed.
@Data
@EqualsAndHashCode(callSuper = false)
public class WealthRoleEntity extends GenericEntity {

	private static final long serialVersionUID = 1L;

	private String role;
	private String label;
	private boolean locked;

	@Override
	public String tableName() {
		return "WEALTH_ROLES";
	}

	@Override
	public String primaryKey() {
		return "role";
	}
}
