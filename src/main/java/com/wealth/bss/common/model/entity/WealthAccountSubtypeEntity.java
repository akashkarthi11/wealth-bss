package com.wealth.bss.common.model.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

// Shared by every user: the account types (category CASA, INVESTMENT or LIABILITY; valued by balance or by holdings).
@Data
@EqualsAndHashCode(callSuper = false)
public class WealthAccountSubtypeEntity extends GenericEntity {

	private static final long serialVersionUID = 1L;

	private String code;
	private String category;
	private String label;
	private String valuation;
	private Integer sortOrder;

	@Override
	public String tableName() {
		return "WEALTH_ACCOUNT_SUBTYPES";
	}

	@Override
	public String primaryKey() {
		return "code";
	}
}
