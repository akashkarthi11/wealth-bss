package com.wealth.bss.serviceinterface;

import java.util.List;

import com.wealth.common.model.dto.AccountIdRequestDto;
import com.wealth.common.model.dto.IdRequestDto;
import com.wealth.common.model.dto.account.AccountDetailDto;
import com.wealth.common.model.dto.account.AccountSplitDto;
import com.wealth.common.model.dto.account.AccountSubtypeDto;
import com.wealth.common.model.dto.account.AccountsOverviewDto;

// Every call reads only the signed-in user's rows (user id from the token context).
public interface IfAccountsService {

	// Active accounts grouped by bank, each with its latest balance, plus totals for the user and for each bank.
	AccountsOverviewDto overview();

	// One account with its balance history and splits; WEALTH_ACCOUNT_NOT_FOUND when it is not the caller's.
	AccountDetailDto detail(IdRequestDto request);

	// The account types, for forms and uploads.
	List<AccountSubtypeDto> subtypes();

	// The active splits of one account with their estimated balances.
	List<AccountSplitDto> splits(AccountIdRequestDto request);
}
