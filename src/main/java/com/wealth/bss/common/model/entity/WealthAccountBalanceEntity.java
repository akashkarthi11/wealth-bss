package com.wealth.bss.common.model.entity;

import java.math.BigDecimal;
import java.sql.Timestamp;

import lombok.Data;
import lombok.EqualsAndHashCode;

// One balance of one account at one moment (primary key account_id + as_of). Also holds a row of the latest-balance view.
@Data
@EqualsAndHashCode(callSuper = false)
public class WealthAccountBalanceEntity extends GenericEntity {

	private static final long serialVersionUID = 1L;

	// Not a column: the caller. A write is accepted only for this user's own account.
	private String userId;
	private String accountId;
	private Timestamp asOf;
	private BigDecimal balance;
	private String status;
	private String note;
	private String enteredBy;
	private Timestamp confirmedAt;
	private Timestamp rejectedAt;
	private String rejectedReason;

	@Override
	public String tableName() {
		return "WEALTH_ACCOUNT_BALANCES";
	}

	@Override
	public String primaryKey() {
		return "account_id, as_of";
	}
}
