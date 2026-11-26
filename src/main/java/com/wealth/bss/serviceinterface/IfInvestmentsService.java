package com.wealth.bss.serviceinterface;

import com.wealth.common.model.dto.invest.InvestmentsDto;

// Reads only the signed-in user's holdings (user id from the token context).
public interface IfInvestmentsService {

	// Every active holding with its instrument, latest price, current value and gain, largest value first, plus totals in rupees over the priced rupee holdings.
	InvestmentsDto list();
}
