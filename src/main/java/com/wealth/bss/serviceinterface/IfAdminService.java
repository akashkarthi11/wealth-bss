package com.wealth.bss.serviceinterface;

import java.util.List;

import com.wealth.common.model.dto.admin.RecordsRequestDto;
import com.wealth.common.model.dto.admin.RoleCreateRequestDto;
import com.wealth.common.model.dto.admin.RoleDto;
import com.wealth.common.model.dto.admin.UserAccessDto;
import com.wealth.common.model.dto.admin.UserRoleRequestDto;
import com.wealth.common.model.dto.admin.RoleMenusDto;
import com.wealth.common.model.dto.admin.RolesOverviewDto;
import com.wealth.common.model.dto.admin.SnapshotDto;

// Every method is admin only: it throws ADMIN_ONLY_ACTION for any other caller.
public interface IfAdminService {

	// The menus, and each role with its user count and the menus it sees.
	RolesOverviewDto rolesOverview();

	// Chooses the menus of the user role. The admin role is locked and always sees every menu.
	RoleMenusDto updateRoleMenus(RoleMenusDto request);

	// Adds a role from its name (the key is made from it). It starts with the Overview menu only. ROLE_EXISTS when the key is taken.
	RoleDto createRole(RoleCreateRequestDto request);

	// Every user with their role.
	List<UserAccessDto> listUsers();

	// Gives a user another role and signs them out. Refused: your own role, the last admin, a role that does not exist.
	UserAccessDto setUserRole(UserRoleRequestDto request);

	// The caller's saved records, newest first, optionally filtered by name or key.
	List<SnapshotDto> listRecords(RecordsRequestDto request);
}
