package com.wealth.bss.common.model.entity;

import java.math.BigDecimal;

import lombok.Data;
import lombok.EqualsAndHashCode;

// A round-off that closed a person's items into one amount. (table wealth_person_rollups)
@Data
@EqualsAndHashCode(callSuper = false)
public class WealthPersonRollupEntity extends GenericEntity {

	private static final long serialVersionUID = 1L;

	private String id;
	private String personId;
	private String on;
	private BigDecimal totalOwed;
	private BigDecimal alreadyBack;
	private BigDecimal newAmount;
	private int closedItems;
	private String note;

	@Override
	public String tableName() {
		return "WEALTH_PERSON_ROLLUPS";
	}

	@Override
	public String primaryKey() {
		return "id";
	}
}
