package com.wealth.bss.serviceinterface;

import java.util.List;

import com.wealth.common.model.dto.admin.RecordsRequestDto;
import com.wealth.common.model.dto.admin.RoleMenusDto;
import com.wealth.common.model.dto.admin.RoleMenusRequestDto;
import com.wealth.common.model.dto.admin.RolesOverviewDto;
import com.wealth.common.model.dto.admin.SnapshotDto;

// Every method is admin only: it throws ADMIN_ONLY_ACTION for any other caller.
public interface IfAdminService {

	// The menus, and each role with its user count and the menus it sees.
	RolesOverviewDto rolesOverview();

	// Chooses the menus of the user role. The admin role is locked and always sees every menu.
	RoleMenusDto updateRoleMenus(RoleMenusRequestDto request);

	// The caller's saved records, newest first, optionally filtered by name or key.
	List<SnapshotDto> listRecords(RecordsRequestDto request);
}
