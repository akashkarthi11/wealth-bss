package com.wealth.bss.common.model.entity;

import java.math.BigDecimal;

import lombok.Data;
import lombok.EqualsAndHashCode;

// Money lent to or paid back by a person; txDescription and account come from the linked transfer. (table wealth_person_entries)
@Data
@EqualsAndHashCode(callSuper = false)
public class WealthPersonEntryEntity extends GenericEntity {

	private static final long serialVersionUID = 1L;

	private String id;
	private String personId;
	private String direction;
	private BigDecimal amount;
	private String on;
	private String transactionId;
	private String note;
	private String splitId;
	private String rollupId;
	private String rollupRef;
	private String status;
	private String txDescription;
	private String account;

	@Override
	public String tableName() {
		return "WEALTH_PERSON_ENTRIES";
	}

	@Override
	public String primaryKey() {
		return "id";
	}
}
