package com.wealth.bss.common.model.dao;

import java.util.List;

import com.wealth.bss.common.model.dto.PaginationDto;
import com.wealth.bss.common.model.entity.WealthAccountSplitBalanceEntity;
import com.wealth.bss.common.model.entity.WealthAccountSplitEntity;
import com.wealth.bss.common.model.entity.WealthAccountSplitMoveEntity;

// Read only for now; recording a split balance or a move is added with the accounts service.
public interface IfWealthAccountSplitDao {

	// userId and id (the account); its active splits in display order.
	List<WealthAccountSplitEntity> findByAccount(PaginationDto paginationDto);

	// userId and id (the account); the latest balance of each active split.
	List<WealthAccountSplitBalanceEntity> findLatestBalancesByAccount(PaginationDto paginationDto);

	// userId and id (the account); the moves of its active splits, newest first.
	List<WealthAccountSplitMoveEntity> findMovesByAccount(PaginationDto paginationDto);
}
