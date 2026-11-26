package com.wealth.bss.common.model.entity;

import java.math.BigDecimal;

import lombok.Data;
import lombok.EqualsAndHashCode;

// A pot inside one account (for example Rent). Never part of the account balance or the totals.
@Data
@EqualsAndHashCode(callSuper = false)
public class WealthAccountSplitEntity extends GenericEntity {

	private static final long serialVersionUID = 1L;

	private String id;
	private String userId;
	private String accountId;
	private String splitKey;
	private String name;
	private String kind;
	private BigDecimal goalAmount;
	private String purpose;
	private Integer sortOrder;
	private String status;

	@Override
	public String tableName() {
		return "WEALTH_ACCOUNT_SPLITS";
	}

	@Override
	public String primaryKey() {
		return "id";
	}
}
