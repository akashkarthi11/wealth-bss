package com.wealth.bss.common.model.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

// One menu or submenu (table wealth_menus). The table is the only place the navigation and the permission list are defined.
@Data
@EqualsAndHashCode(callSuper = false)
public class WealthMenuEntity extends GenericEntity {

	private static final long serialVersionUID = 1L;

	private String menuKey;
	private String parentKey;
	private String label;
	private String route;
	private String menuGroup;
	private Integer sortOrder;
	private boolean adminOnly;
	private boolean active;

	@Override
	public String tableName() {
		return "WEALTH_MENUS";
	}

	@Override
	public String primaryKey() {
		return "menu_key";
	}
}
