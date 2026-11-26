package com.wealth.bss.common.model.dao;

import java.util.List;

import com.wealth.bss.common.model.dto.PaginationDto;
import com.wealth.bss.common.model.entity.WealthAccountEntity;
import com.wealth.bss.common.model.entity.WealthAccountSubtypeEntity;

public interface IfWealthAccountDao {

	// userId is the caller; every account of that user, by bank and name.
	List<WealthAccountEntity> findByUser(PaginationDto paginationDto);

	// userId and id; null when the account is not the caller's.
	WealthAccountEntity findById(PaginationDto paginationDto);

	// The account types, shared by every user.
	List<WealthAccountSubtypeEntity> findAllSubtypes();

	// entity.userId is the owner. Returns null when the account key already exists for that user.
	WealthAccountEntity insert(WealthAccountEntity entity);

	// Only the fixed-deposit columns of the entity are written; a null field keeps the stored value. Returns null when the account is not the owner's.
	WealthAccountEntity updateFdTerms(WealthAccountEntity entity);
}
