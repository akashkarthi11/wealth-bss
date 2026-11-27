package com.wealth.bss.common.model.entity;

import java.math.BigDecimal;

import lombok.Data;
import lombok.EqualsAndHashCode;

// A bill split between the owner and other people. (table wealth_bill_splits)
@Data
@EqualsAndHashCode(callSuper = false)
public class WealthBillSplitEntity extends GenericEntity {

	private static final long serialVersionUID = 1L;

	private String id;
	private String transactionId;
	private String title;
	private String on;
	private BigDecimal total;
	private BigDecimal tax;
	private String mode;
	private String note;

	@Override
	public String tableName() {
		return "WEALTH_BILL_SPLITS";
	}

	@Override
	public String primaryKey() {
		return "id";
	}
}
