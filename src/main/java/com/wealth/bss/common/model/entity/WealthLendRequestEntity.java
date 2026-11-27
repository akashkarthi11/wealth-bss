package com.wealth.bss.common.model.entity;

import java.math.BigDecimal;
import java.sql.Timestamp;

import lombok.Data;
import lombok.EqualsAndHashCode;

// An "I've paid" request from a person; name is the person's name. (table wealth_lend_requests)
@Data
@EqualsAndHashCode(callSuper = false)
public class WealthLendRequestEntity extends GenericEntity {

	private static final long serialVersionUID = 1L;

	private String id;
	private String personId;
	private String name;
	private BigDecimal amount;
	private String paidOn;
	private String method;
	private String note;
	private Timestamp createdAt;

	@Override
	public String tableName() {
		return "WEALTH_LEND_REQUESTS";
	}

	@Override
	public String primaryKey() {
		return "id";
	}
}
