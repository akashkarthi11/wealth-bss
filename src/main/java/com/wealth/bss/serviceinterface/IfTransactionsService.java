package com.wealth.bss.serviceinterface;

import com.wealth.common.model.dto.IdRequestDto;
import com.wealth.common.model.dto.transaction.TransactionCreateRequestDto;
import com.wealth.common.model.dto.transaction.TransactionDto;
import com.wealth.common.model.dto.transaction.TransactionPageDto;
import com.wealth.common.model.dto.transaction.TransactionPageRequestDto;

// The signed-in user's own transactions (user id from the token context).
public interface IfTransactionsService {

	// One page, newest first, narrowed by the filters in the request. INVALID_REQUEST for a size outside 1 to 200, a kind that is not known, an id or day of the wrong shape, from after to,
	// a search of more than 100 characters or a cursor that was not made by this list. The first page without filters is read from the nexus cache.
	TransactionPageDto list(TransactionPageRequestDto request);

	// The dashboard for the same filters (search, kind, account, dates), over every matching transaction: money in, money out, net, count and the split by category, account and month.
	// Transfers, copies and dismissed entries are left out. INVALID_REQUEST for a filter the list would refuse.
	com.wealth.common.model.dto.transaction.TransactionSummaryDto summary(TransactionPageRequestDto request);

	// The Refresh button: asks for a fresh read of the first page at once (at most every few seconds per user) and returns what is cached now.
	TransactionPageDto refresh();

	// Adds an entry. It starts pending: only accepting it (a later step) changes a balance. TRANSACTION_INVALID for a bad kind, category, amount or date;
	// TRANSACTION_ACCOUNT_INVALID when accountId is not the caller's; TRANSACTION_REFERENCE_EXISTS when externalRef was recorded before. A name that matches no account leaves it unassigned.
	TransactionDto create(TransactionCreateRequestDto request);

	// Removes the caller's transaction with the cashback written for it; false when there was none. Balances are not reversed.
	Boolean delete(IdRequestDto request);
}
