package com.wealth.bss.serviceinterface;

import java.util.List;

import com.wealth.common.model.dto.AccountIdRequestDto;
import com.wealth.common.model.dto.IdRequestDto;
import com.wealth.common.model.dto.account.AccountCreateRequestDto;
import com.wealth.common.model.dto.account.BalanceRecordRequestDto;
import com.wealth.common.model.dto.account.BalanceRecordResultDto;
import com.wealth.common.model.dto.account.FdTermsRequestDto;
import com.wealth.common.model.dto.account.AccountDetailDto;
import com.wealth.common.model.dto.account.AccountDto;
import com.wealth.common.model.dto.account.AccountSplitDto;
import com.wealth.common.model.dto.account.AccountSubtypeDto;
import com.wealth.common.model.dto.account.AccountsOverviewDto;

// Every call reads only the signed-in user's rows (user id from the token context).
public interface IfAccountsService {

	// Active accounts grouped by bank, each with its latest balance, plus totals for the user and for each bank.
	AccountsOverviewDto overview();

	// The dashboard Refresh button: asks for a fresh read at once (at most every few seconds per user) and returns what is cached now; fetchedAt tells when it was read.
	AccountsOverviewDto refresh();

	// Admin only. Adds an account for the caller (the bank is the institution text, so a bank exists once it has an account). A fixed deposit may carry its terms.
	// ACCOUNT_TYPE_INVALID, ACCOUNT_DETAILS_INVALID, ACCOUNT_CURRENCY_INVALID, FD_TERMS_INVALID, ACCOUNT_EXISTS (same institution, type and name).
	AccountDto create(AccountCreateRequestDto request);

	// Admin only. Records a balance for one of the caller's active accounts that is valued by balance (not by holdings). status is verified (from the bank) or reported (an
	// estimate); pending_reconciliation is never used. asOf defaults to now and must not be in the future. A second balance at the same moment is not stored (stored = false).
	// A verified, newer balance of a savings or current account is also copied onto the account row. dryRun stores nothing.
	BalanceRecordResultDto recordBalance(BalanceRecordRequestDto request);

	// Admin only. Changes the terms of one of the caller's fixed deposits; a term that is not sent keeps its value. FD_TERMS_INVALID for another type or terms that do not fit.
	AccountDto updateFdTerms(FdTermsRequestDto request);

	// One account with its balance history and splits; WEALTH_ACCOUNT_NOT_FOUND when it is not the caller's.
	AccountDetailDto detail(IdRequestDto request);

	// The account types, for forms and uploads.
	List<AccountSubtypeDto> subtypes();

	// The active splits of one account with their estimated balances.
	List<AccountSplitDto> splits(AccountIdRequestDto request);
}
