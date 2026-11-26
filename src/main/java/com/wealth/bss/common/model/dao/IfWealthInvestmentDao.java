package com.wealth.bss.common.model.dao;

import java.util.List;

import com.wealth.bss.common.model.entity.WealthHoldingEntity;

public interface IfWealthInvestmentDao {

	// actor is "owner" (a person pressed Fetch) or "cron" (the daily run). One transaction for the account (accountId): each share is added to wealth_instruments if new (an existing one is left as it is), its holding is set active
	// with the quantity, invested value and time, and its price is added to wealth_instrument_prices under priceSource. Holdings of the account that are not in the
	// list and are not closed are closed with quantity 0. Returns {saved, closed}.
	int[] saveBrokerHoldings(String actor, String accountId, String priceSource, List<WealthHoldingEntity> holdings);
}
