package com.wealth.bss.common.model.entity;

import java.math.BigDecimal;
import java.sql.Timestamp;

import lombok.Data;
import lombok.EqualsAndHashCode;

// What an account holds of one instrument (table wealth_holdings, one row per account and instrument).
// instrument and lastPrice are not columns: they carry the share and its latest price into the same save (the price goes to wealth_instrument_prices).
@Data
@EqualsAndHashCode(callSuper = false)
public class WealthHoldingEntity extends GenericEntity {

	private static final long serialVersionUID = 1L;

	private String accountId;
	private String instrumentId;
	private BigDecimal quantity;
	private BigDecimal investedValue;
	private Timestamp asOf;
	private String status;
	private WealthInstrumentEntity instrument;
	private BigDecimal lastPrice;

	@Override
	public String tableName() {
		return "WEALTH_HOLDINGS";
	}

	@Override
	public String primaryKey() {
		return "accountId";
	}
}
