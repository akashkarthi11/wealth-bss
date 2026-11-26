package com.wealth.bss.common.model.entity;

import java.math.BigDecimal;
import java.sql.Timestamp;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class WealthAccountSplitBalanceEntity extends GenericEntity {

	private static final long serialVersionUID = 1L;

	private String id;
	private String splitId;
	private Timestamp asOf;
	private BigDecimal balance;
	private String status;
	private String note;

	@Override
	public String tableName() {
		return "WEALTH_ACCOUNT_SPLIT_BALANCES";
	}

	@Override
	public String primaryKey() {
		return "id";
	}
}
