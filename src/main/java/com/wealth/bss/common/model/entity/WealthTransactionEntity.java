package com.wealth.bss.common.model.entity;

import java.math.BigDecimal;

import lombok.Data;
import lombok.EqualsAndHashCode;

// A bank transfer not linked to a person yet (only what the People list needs). (table wealth_transactions)
@Data
@EqualsAndHashCode(callSuper = false)
public class WealthTransactionEntity extends GenericEntity {

	private static final long serialVersionUID = 1L;

	private String id;
	private String description;
	private String category;
	private BigDecimal amount;
	private String day;

	@Override
	public String tableName() {
		return "WEALTH_TRANSACTIONS";
	}

	@Override
	public String primaryKey() {
		return "id";
	}
}
