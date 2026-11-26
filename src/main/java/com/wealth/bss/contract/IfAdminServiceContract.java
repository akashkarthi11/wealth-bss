package com.wealth.bss.contract;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import com.wealth.bss.constants.WealthServiceConstants;
import com.wealth.common.generic.ApiResponse;
import com.wealth.common.generic.KarthiException;
import com.wealth.common.model.dto.admin.RecordsRequestDto;
import com.wealth.common.model.dto.admin.RoleCreateRequestDto;
import com.wealth.common.model.dto.admin.RoleDto;
import com.wealth.common.model.dto.admin.UserAccessDto;
import com.wealth.common.model.dto.admin.UserRoleRequestDto;
import com.wealth.common.model.dto.admin.RoleMenusDto;
import com.wealth.common.model.dto.admin.RolesOverviewDto;
import com.wealth.common.model.dto.admin.SnapshotDto;

// Admin only. Not public: the interceptor needs a valid token, and the service checks the role.
@RequestMapping(WealthServiceConstants.ADMIN_BASE)
public interface IfAdminServiceContract {

	@PostMapping(WealthServiceConstants.ROLES_LIST)
	ResponseEntity<ApiResponse<RolesOverviewDto, Void>> rolesList() throws KarthiException;

	@PostMapping(WealthServiceConstants.ROLES_UPDATE_MENUS)
	ResponseEntity<ApiResponse<RoleMenusDto, Void>> updateRoleMenus(@RequestBody RoleMenusDto request) throws KarthiException;

	@PostMapping(WealthServiceConstants.ROLES_CREATE)
	ResponseEntity<ApiResponse<RoleDto, Void>> createRole(@RequestBody RoleCreateRequestDto request) throws KarthiException;

	@PostMapping(WealthServiceConstants.USERS_LIST)
	ResponseEntity<ApiResponse<List<UserAccessDto>, Void>> usersList() throws KarthiException;

	@PostMapping(WealthServiceConstants.USERS_SET_ROLE)
	ResponseEntity<ApiResponse<UserAccessDto, Void>> setUserRole(@RequestBody UserRoleRequestDto request) throws KarthiException;

	@PostMapping(WealthServiceConstants.RECORDS_LIST)
	ResponseEntity<ApiResponse<List<SnapshotDto>, Void>> recordsList(@RequestBody(required = false) RecordsRequestDto request)
			throws KarthiException;
}
