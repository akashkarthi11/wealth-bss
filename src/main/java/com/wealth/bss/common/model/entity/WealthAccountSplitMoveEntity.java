package com.wealth.bss.common.model.entity;

import java.math.BigDecimal;
import java.sql.Timestamp;

import lombok.Data;
import lombok.EqualsAndHashCode;

// Money into (+) or out of (-) a split, optionally linked to a transaction.
@Data
@EqualsAndHashCode(callSuper = false)
public class WealthAccountSplitMoveEntity extends GenericEntity {

	private static final long serialVersionUID = 1L;

	private String id;
	private String splitId;
	private Timestamp occurredAt;
	private BigDecimal amount;
	private String transactionId;
	private String note;

	@Override
	public String tableName() {
		return "WEALTH_ACCOUNT_SPLIT_MOVES";
	}

	@Override
	public String primaryKey() {
		return "id";
	}
}
