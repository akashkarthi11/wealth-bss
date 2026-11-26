package com.wealth.bss.common.model.dao;

import com.wealth.bss.common.model.dto.PaginationDto;
import com.wealth.bss.common.model.entity.WealthRoleMenuEntity;

// Reading a role's menus is IfWealthUserDao.findMenusByRole; this DAO adds what the admin screens need.
public interface IfWealthRoleDao {

	// searchText carries the role; the number of users with it.
	long countUsersByRole(PaginationDto paginationDto);

	// Creates or replaces the menus of entity.role (menus as a comma-separated list, updatedBy = the admin). Returns the saved row.
	WealthRoleMenuEntity saveMenus(WealthRoleMenuEntity entity);
}
