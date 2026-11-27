package com.wealth.bss.serviceinterface;

import com.wealth.common.model.dto.invest.HoldingDto;
import com.wealth.common.model.dto.invest.HoldingSaveRequestDto;
import com.wealth.common.model.dto.invest.InvestmentsDto;

// Reads only the signed-in user's holdings (user id from the token context).
public interface IfInvestmentsService {

	// Every active holding with its instrument, latest price, current value and gain, largest value first, plus totals in rupees over the priced rupee holdings.
	InvestmentsDto list();

	// The Refresh button: asks for a fresh read at once (at most every few seconds per user) and returns what is cached now; fetchedAt tells when it was read.
	InvestmentsDto refresh();

	// Admin only. Adds or corrects one holding of the caller's investment account (valued by holdings) by hand: a mutual fund, digital gold or silver, a broker account that cannot be
	// fetched. HOLDING_ACCOUNT_INVALID when the account is not the caller's, not active, not valued by holdings or the asset class does not fit its type; HOLDING_INVALID for a bad
	// name, symbol, unit, quantity, value, price or date. The price is saved as a manual price on that day and the lists are dropped from the cache.
	HoldingDto saveHolding(HoldingSaveRequestDto request);
}
