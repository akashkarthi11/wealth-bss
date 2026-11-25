package com.wealth.bss.common.model.dao;

import com.wealth.bss.common.model.dto.PaginationDto;
import com.wealth.bss.common.model.entity.WealthUserCredentialEntity;
import com.wealth.bss.common.model.entity.WealthUserEntity;

public interface IfWealthUserDao {

	// Returns null when no row matches the id.
	WealthUserEntity findById(PaginationDto paginationDto);

	// searchText carries the email; returns null when unknown.
	WealthUserCredentialEntity findCredentialByEmail(PaginationDto paginationDto);

	// Returns null when the email already exists.
	WealthUserEntity insertUser(WealthUserCredentialEntity credential);

	// Bumps the session version; returns null when the email is unknown.
	WealthUserEntity updatePassword(WealthUserCredentialEntity credential);

	// searchText carries the role; returns a comma-separated menu list, or null when none is saved.
	String findMenusByRole(PaginationDto paginationDto);
}
