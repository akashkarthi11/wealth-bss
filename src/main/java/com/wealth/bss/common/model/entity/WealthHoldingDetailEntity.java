package com.wealth.bss.common.model.entity;

import java.math.BigDecimal;
import java.sql.Timestamp;

import lombok.Data;
import lombok.EqualsAndHashCode;

// One row of the investments list: an active holding with its account, instrument and latest price (no table of its own).
@Data
@EqualsAndHashCode(callSuper = false)
public class WealthHoldingDetailEntity extends GenericEntity {

	private static final long serialVersionUID = 1L;

	private String accountId;
	private String bankName;
	private String accountName;
	private String accountCurrency;
	private String instrumentId;
	private String symbol;
	private String name;
	private String assetClass;
	private String unit;
	private String currency;
	private String priceSourceSymbol;
	private String isin;
	private String exchange;
	private String product;
	private BigDecimal t1Quantity;
	private BigDecimal collateralQuantity;
	private BigDecimal haircut;
	private BigDecimal brokerClose;
	private BigDecimal brokerPnl;
	private BigDecimal brokerPnlPercent;
	private BigDecimal quantity;
	private BigDecimal investedValue;
	private Timestamp holdingAsOf;
	private String status;
	private BigDecimal price;
	private Timestamp priceAsOf;
	private String priceSource;
	private BigDecimal fxToInr;

	@Override
	public String tableName() {
		return "WEALTH_HOLDINGS";
	}

	@Override
	public String primaryKey() {
		return "accountId, instrumentId";
	}
}
