package com.wealth.bss.common.model.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

// One tradable instrument (table wealth_instruments). Fetched Angel One shares are asset class indian_equity, unit share, currency INR.
@Data
@EqualsAndHashCode(callSuper = false)
public class WealthInstrumentEntity extends GenericEntity {

	private static final long serialVersionUID = 1L;

	private String id;
	private String assetClass;
	private String symbol;
	private String name;
	private String currency;
	private String unit;
	private String priceSource;
	private String priceSourceSymbol;

	@Override
	public String tableName() {
		return "WEALTH_INSTRUMENTS";
	}

	@Override
	public String primaryKey() {
		return "id";
	}
}
