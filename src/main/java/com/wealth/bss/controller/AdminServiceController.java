package com.wealth.bss.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.wealth.bss.contract.IfAdminServiceContract;
import com.wealth.bss.serviceinterface.IfAdminService;
import com.wealth.common.generic.ApiResponse;
import com.wealth.common.generic.ApiResponseTemplate;
import com.wealth.common.generic.BaseError;
import com.wealth.common.generic.KarthiException;
import com.wealth.common.logger.KarthiLogger;
import com.wealth.common.model.dto.admin.RecordsRequestDto;
import com.wealth.common.model.dto.admin.RoleCreateRequestDto;
import com.wealth.common.model.dto.admin.RoleDto;
import com.wealth.common.model.dto.admin.UserAccessDto;
import com.wealth.common.model.dto.admin.UserRoleRequestDto;
import com.wealth.common.model.dto.admin.RoleMenusDto;
import com.wealth.common.model.dto.admin.RolesOverviewDto;
import com.wealth.common.model.dto.admin.SnapshotDto;

@RestController
public class AdminServiceController implements IfAdminServiceContract {

	private static final KarthiLogger LOGGER = new KarthiLogger(AdminServiceController.class);

	private final IfAdminService adminService;
	private final ApiResponseTemplate responseTemplate;

	public AdminServiceController(IfAdminService adminService, ApiResponseTemplate responseTemplate) {
		this.adminService = adminService;
		this.responseTemplate = responseTemplate;
	}

	@Override
	public ResponseEntity<ApiResponse<RolesOverviewDto, Void>> rolesList() throws KarthiException {
		LOGGER.info("Entering rolesList");
		return responseTemplate.apires(adminService.rolesOverview(), BaseError.Success);
	}

	@Override
	public ResponseEntity<ApiResponse<RoleMenusDto, Void>> updateRoleMenus(RoleMenusDto request) throws KarthiException {
		LOGGER.info("Entering updateRoleMenus");
		if (request == null) {
			throw new KarthiException(BaseError.INVALID_REQUEST);
		}
		return responseTemplate.apires(adminService.updateRoleMenus(request), BaseError.Success);
	}

	@Override
	public ResponseEntity<ApiResponse<RoleDto, Void>> createRole(RoleCreateRequestDto request) throws KarthiException {
		LOGGER.info("Entering createRole");
		return responseTemplate.apires(adminService.createRole(request), BaseError.Success);
	}

	@Override
	public ResponseEntity<ApiResponse<List<UserAccessDto>, Void>> usersList() throws KarthiException {
		LOGGER.info("Entering usersList");
		return responseTemplate.apires(adminService.listUsers(), BaseError.Success);
	}

	@Override
	public ResponseEntity<ApiResponse<UserAccessDto, Void>> setUserRole(UserRoleRequestDto request) throws KarthiException {
		LOGGER.info("Entering setUserRole");
		if (request == null) {
			throw new KarthiException(BaseError.INVALID_REQUEST);
		}
		return responseTemplate.apires(adminService.setUserRole(request), BaseError.Success);
	}

	@Override
	public ResponseEntity<ApiResponse<List<SnapshotDto>, Void>> recordsList(RecordsRequestDto request) throws KarthiException {
		LOGGER.info("Entering recordsList");
		return responseTemplate.apires(adminService.listRecords(request), BaseError.Success);
	}
}
