package com.wealth.bss.common.model.dao;

import java.util.List;

import com.wealth.bss.common.model.dto.PaginationDto;
import com.wealth.bss.common.model.entity.WealthBillSplitEntity;
import com.wealth.bss.common.model.entity.WealthBillSplitShareEntity;
import com.wealth.bss.common.model.entity.WealthLendCommentEntity;
import com.wealth.bss.common.model.entity.WealthLendRequestEntity;
import com.wealth.bss.common.model.entity.WealthPersonAliasEntity;
import com.wealth.bss.common.model.entity.WealthPersonEntity;
import com.wealth.bss.common.model.entity.WealthPersonEntryEntity;
import com.wealth.bss.common.model.entity.WealthPersonRollupEntity;
import com.wealth.bss.common.model.entity.WealthTransactionEntity;

// Reads for the People list. userId (in the PaginationDto) is the caller; every method returns that user's own rows only.
public interface IfWealthPeopleDao {

	// People that are not removed, by name.
	List<WealthPersonEntity> findPeople(PaginationDto paginationDto);

	// Entries that are not removed, newest first, each with its linked transfer's description and account.
	List<WealthPersonEntryEntity> findEntries(PaginationDto paginationDto);

	// Shares of active splits (a share with no person is the owner's own part).
	List<WealthBillSplitShareEntity> findShares(PaginationDto paginationDto);

	// Active splits, newest first.
	List<WealthBillSplitEntity> findSplits(PaginationDto paginationDto);

	// Round-offs, newest first.
	List<WealthPersonRollupEntity> findRollups(PaginationDto paginationDto);

	// Pending "I've paid" requests, newest first, at most 50.
	List<WealthLendRequestEntity> findPendingRequests(PaginationDto paginationDto);

	// Other names people go by.
	List<WealthPersonAliasEntity> findAliases(PaginationDto paginationDto);

	// Remarks on entries and shares, oldest first.
	List<WealthLendCommentEntity> findComments(PaginationDto paginationDto);

	// Transfers sent or received that no entry is linked to yet, oldest first.
	List<WealthTransactionEntity> findUnlinkedTransfers(PaginationDto paginationDto);
}
