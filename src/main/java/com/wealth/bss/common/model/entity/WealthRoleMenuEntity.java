package com.wealth.bss.common.model.entity;


import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class WealthRoleMenuEntity extends GenericEntity {

	private static final long serialVersionUID = 1L;

	private String role;
	// The table column is text[]; it is read and written as a comma-separated list.
	private String menus;
	private String updatedBy;

	@Override
	public String tableName() {
		return "WEALTH_ROLE_MENUS";
	}

	@Override
	public String primaryKey() {
		return "role";
	}
}
