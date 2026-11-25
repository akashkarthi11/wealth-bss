package com.wealth.bss.common.model.entity;

import java.io.Serializable;

// Base of every entity; the GrowX audit columns are not part of Wealth tables.
public abstract class GenericEntity implements Serializable {

	private static final long serialVersionUID = 1L;

	public abstract String tableName();

	public abstract String primaryKey();
}
