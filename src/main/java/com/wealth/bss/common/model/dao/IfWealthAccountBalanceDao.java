package com.wealth.bss.common.model.dao;

import java.util.List;

import com.wealth.bss.common.model.dto.PaginationDto;
import com.wealth.bss.common.model.entity.WealthAccountBalanceEntity;

public interface IfWealthAccountBalanceDao {

	// userId is the caller; the latest balance of each of that user's accounts that has one.
	List<WealthAccountBalanceEntity> findLatestByUser(PaginationDto paginationDto);

	// userId and id (the account); newest first, rejected rows left out, pageSize rows (30 when 0).
	List<WealthAccountBalanceEntity> findHistory(PaginationDto paginationDto);

	// entity.userId, accountId, asOf, balance, status, note, enteredBy. Returns null when a balance already exists at that moment
	// or the account is not the caller's.
	WealthAccountBalanceEntity insert(WealthAccountBalanceEntity entity);

	// Copies the balance onto the account row for the old app, only forward in time. Returns false when nothing changed.
	boolean updateAccountCopy(WealthAccountBalanceEntity entity);
}
