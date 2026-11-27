package com.wealth.bss.common.model.entity;

import java.math.BigDecimal;
import java.sql.Timestamp;

import lombok.Data;
import lombok.EqualsAndHashCode;

// One of the owner's contacts (not an app user). (table wealth_people)
@Data
@EqualsAndHashCode(callSuper = false)
public class WealthPersonEntity extends GenericEntity {

	private static final long serialVersionUID = 1L;

	private String id;
	private String name;
	private String kind;
	private String dueOn;
	private BigDecimal annualRate;
	private String status;
	private String note;
	private Timestamp shareCreatedAt;
	private boolean hidden;

	@Override
	public String tableName() {
		return "WEALTH_PEOPLE";
	}

	@Override
	public String primaryKey() {
		return "id";
	}
}
