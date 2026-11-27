package com.wealth.bss.common.model.entity;

import java.math.BigDecimal;

import lombok.Data;
import lombok.EqualsAndHashCode;

// One person's share of a split bill (no person = the owner's own part). (table wealth_bill_split_shares)
@Data
@EqualsAndHashCode(callSuper = false)
public class WealthBillSplitShareEntity extends GenericEntity {

	private static final long serialVersionUID = 1L;

	private String id;
	private String splitId;
	private String personId;
	private BigDecimal base;
	private BigDecimal tax;
	private BigDecimal amount;
	private String rollupId;
	private String status;

	@Override
	public String tableName() {
		return "WEALTH_BILL_SPLIT_SHARES";
	}

	@Override
	public String primaryKey() {
		return "id";
	}
}
