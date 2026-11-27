package com.wealth.bss.common.model.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

// Another name a person goes by in bank text. (table wealth_person_aliases)
@Data
@EqualsAndHashCode(callSuper = false)
public class WealthPersonAliasEntity extends GenericEntity {

	private static final long serialVersionUID = 1L;

	private String personId;
	private String alias;

	@Override
	public String tableName() {
		return "WEALTH_PERSON_ALIASES";
	}

	@Override
	public String primaryKey() {
		return "id";
	}
}
