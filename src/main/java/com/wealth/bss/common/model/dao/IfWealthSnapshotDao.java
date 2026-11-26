package com.wealth.bss.common.model.dao;

import java.util.List;

import com.wealth.bss.common.model.dto.PaginationDto;
import com.wealth.bss.common.model.entity.WealthSnapshotEntity;

public interface IfWealthSnapshotDao {

	// userId is the caller; searchText is optional and matches the source name or key. Newest first.
	List<WealthSnapshotEntity> findByUser(PaginationDto paginationDto);
}
