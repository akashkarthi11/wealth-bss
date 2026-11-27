package com.wealth.bss.common.model.dao;

import java.util.List;

import com.wealth.bss.common.model.dto.PaginationDto;
import com.wealth.bss.common.model.entity.WealthTransactionEntity;

public interface IfWealthTransactionDao {

	// userId is the caller. Newest first, pageSize rows, narrowed by kind, accountId, fromDay, toDay and searchText (empty = no filter) and continuing after the cursor row
	// (cursorDay, cursorCreated, cursorId) when given.
	List<WealthTransactionEntity> findPage(PaginationDto paginationDto);

	// The id of the caller's account that goes by that name (its name, its name with the last digits, or its key); null when none does.
	String findAccountIdByName(String userId, String name);

	// entity.userId is the owner. entity.reviewStatus is stored when given (a transaction with no doubt is accepted at once); empty = waiting for review. Returns null when the user already has that externalRef.
	WealthTransactionEntity insert(WealthTransactionEntity entity);

	// The caller's transactions on that account with that amount and direction (debit or credit) on the day or a day either side, that are not copies or dismissed: what a statement line may be.
	List<WealthTransactionEntity> findStatementMatches(String userId, String accountId, java.math.BigDecimal amount, String direction, String day);

	// The caller's transactions with those ids.
	List<WealthTransactionEntity> findByIds(String userId, List<String> ids);

	// Whether the user has a transaction with that reference.
	boolean referenceExists(String userId, String reference);

	// Whether the account is the user's own.
	boolean accountBelongsTo(String userId, String accountId);

	// Gives the account to the user's transaction with that reference, only when it has none yet. True when one was changed.
	boolean assignAccountIfMissing(String userId, String reference, String accountId);

	// The ids of the caller's active accounts that carry those last digits (one, when the statement can be placed).
	List<String> findAccountIdsByDigits(String userId, String digits);

	// Removes the caller's transaction with its cashback and cancels its offer uses, in one transaction. Returns whether it existed.
	boolean delete(String userId, String id);

	// The dashboard rows for the filters in the page request (user, kind, account, dates, search; not the cursor or size): dimension is total, category, account or month.
	List<com.wealth.common.model.dto.transaction.TransactionSummaryItemDto> summaryBy(PaginationDto page, String dimension);

	// The ids of the user's accounts of that bank whose masked identifier ends in those four digits (bank is a part of the bank name, any case).
	List<String> findAccountIdsByLastDigits(String userId, String lastDigits, String bank);

	// The user's transactions read from Gmail that still wait for a decision, newest first (at most 200).
	List<WealthTransactionEntity> findPendingFromGmail(String userId);

	// Sets review_status ("accepted" or "dismissed") on one pending Gmail transaction of the user. Returns whether one was changed.
	boolean setGmailReviewStatus(String userId, String id, String status);
}
