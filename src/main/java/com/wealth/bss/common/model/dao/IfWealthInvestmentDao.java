package com.wealth.bss.common.model.dao;

import java.util.List;

import com.wealth.bss.common.model.dto.PaginationDto;
import com.wealth.bss.common.model.entity.WealthAccountBalanceEntity;
import com.wealth.bss.common.model.entity.WealthHoldingDetailEntity;
import com.wealth.bss.common.model.entity.WealthHoldingEntity;

public interface IfWealthInvestmentDao {

	// userId is the caller. Every active holding of that user with its account, instrument and latest price, largest value first.
	List<WealthHoldingDetailEntity> findHoldingDetailsByUser(PaginationDto paginationDto);

	// userId is the caller. One row per account that has priced active holdings: accountId, balance (the market value in rupees) and asOf (the newest price used).
	List<WealthAccountBalanceEntity> findHoldingValuesByUser(PaginationDto paginationDto);

	// actor is "owner" (a person pressed Fetch) or "cron" (the daily run). One transaction for the account (accountId): each share is added to wealth_instruments if new (an existing one is left as it is), its holding is set active
	// with the quantity, invested value and time, and its price is added to wealth_instrument_prices under priceSource. Holdings of the account that are not in the
	// list and are not closed are closed with quantity 0. Returns {saved, closed}.
	int[] saveBrokerHoldings(String actor, String accountId, String priceSource, List<WealthHoldingEntity> holdings);
}
