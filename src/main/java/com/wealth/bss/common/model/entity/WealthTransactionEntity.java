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
	private String userId;
	private String kind;
	private String accountId;
	// the account's label: name and last digits, or Unassigned
	private String account;
	private String occurredOn;
	private String externalRef;
	private String reviewStatus;
	private String duplicateOf;
	private String createdKey;
	private boolean inactive;
	// The bill split made from this transaction (splitStatus is null when it has none).
	private int splitPeople;
	private String splitStatus;
	private String splitMode;

	@Override
	public String tableName() {
		return "WEALTH_TRANSACTIONS";
	}

	@Override
	public String primaryKey() {
		return "id";
	}
}
