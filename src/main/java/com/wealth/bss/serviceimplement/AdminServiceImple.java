package com.wealth.bss.serviceimplement;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Service;

import com.wealth.bss.common.model.dao.IfWealthRoleDao;
import com.wealth.bss.common.model.dao.IfWealthSnapshotDao;
import com.wealth.bss.common.model.dao.IfWealthUserDao;
import com.wealth.bss.common.model.dto.PaginationDto;
import com.wealth.bss.common.model.entity.WealthRoleMenuEntity;
import com.wealth.bss.common.model.entity.WealthSnapshotEntity;
import com.wealth.bss.constants.WealthServiceConstants;
import com.wealth.bss.serviceinterface.IfAdminService;
import com.wealth.bss.serviceinterface.IfRequestContextService;
import com.wealth.common.assembler.GenericAssembler;
import com.wealth.common.generic.BaseError;
import com.wealth.common.generic.KarthiException;
import com.wealth.common.logger.KarthiLogger;
import com.wealth.common.model.dto.admin.MenuDto;
import com.wealth.common.model.dto.admin.RecordsRequestDto;
import com.wealth.common.model.dto.admin.RoleDto;
import com.wealth.common.model.dto.admin.RoleMenusDto;
import com.wealth.common.model.dto.admin.RoleMenusRequestDto;
import com.wealth.common.model.dto.admin.RolesOverviewDto;
import com.wealth.common.model.dto.admin.SnapshotDto;
import com.wealth.common.utils.generic.RequestHeaderUtil;

// Rules carried over from the Node roles route: two fixed roles, admin always sees every menu, the user role gets at least one known menu.
@Service
public class AdminServiceImple implements IfAdminService {

	private static final KarthiLogger LOGGER = new KarthiLogger(AdminServiceImple.class);

	private final IfRequestContextService requestContextService;
	private final IfWealthUserDao wealthUserDao;
	private final IfWealthRoleDao wealthRoleDao;
	private final IfWealthSnapshotDao wealthSnapshotDao;

	public AdminServiceImple(IfRequestContextService requestContextService, IfWealthUserDao wealthUserDao, IfWealthRoleDao wealthRoleDao,
			IfWealthSnapshotDao wealthSnapshotDao) {
		this.requestContextService = requestContextService;
		this.wealthUserDao = wealthUserDao;
		this.wealthRoleDao = wealthRoleDao;
		this.wealthSnapshotDao = wealthSnapshotDao;
	}

	@Override
	public RolesOverviewDto rolesOverview() {
		String methodName = "rolesOverview";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		requestContextService.requireAdmin();
		List<MenuDto> menus = new ArrayList<>();
		for (String[] m : WealthServiceConstants.MENUS) {
			MenuDto dto = new MenuDto();
			dto.setKey(m[0]);
			dto.setLabel(m[1]);
			dto.setGroup(m[2]);
			menus.add(dto);
		}
		RolesOverviewDto overview = new RolesOverviewDto();
		overview.setMenus(menus);
		overview.setRoles(List.of(role(WealthServiceConstants.ROLE_ADMIN, "Admin", true, allKeys()),
				role(WealthServiceConstants.ROLE_USER, "User", false, savedMenusOrAll(WealthServiceConstants.ROLE_USER))));
		LOGGER.info(WealthServiceConstants.EXITING + methodName);
		return overview;
	}

	@Override
	public RoleMenusDto updateRoleMenus(RoleMenusRequestDto request) {
		String methodName = "updateRoleMenus";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		requestContextService.requireAdmin();
		if (request == null || request.getRole() == null) {
			throw new KarthiException(BaseError.INVALID_REQUEST);
		}
		if (WealthServiceConstants.ROLE_ADMIN.equals(request.getRole())) {
			throw new KarthiException(BaseError.ROLE_LOCKED);
		}
		if (!WealthServiceConstants.ROLE_USER.equals(request.getRole())) {
			throw new KarthiException(BaseError.UNKNOWN_ROLE);
		}
		if (request.getMenus() == null || request.getMenus().isEmpty()) {
			throw new KarthiException(BaseError.CHOOSE_A_MENU);
		}
		List<String> keys = allKeys();
		if (!keys.containsAll(request.getMenus())) {
			throw new KarthiException(BaseError.UNKNOWN_MENU);
		}
		// Saved in the order of the menu list, without repeats.
		List<String> clean = keys.stream().filter(request.getMenus()::contains).toList();
		WealthRoleMenuEntity entity = new WealthRoleMenuEntity();
		entity.setRole(request.getRole());
		entity.setMenus(String.join(",", clean));
		entity.setUpdatedBy(RequestHeaderUtil.getUserId());
		wealthRoleDao.saveMenus(entity);
		RoleMenusDto saved = new RoleMenusDto();
		saved.setRole(request.getRole());
		saved.setMenus(clean);
		LOGGER.info(WealthServiceConstants.EXITING + methodName);
		return saved;
	}

	@Override
	public List<SnapshotDto> listRecords(RecordsRequestDto request) {
		String methodName = "listRecords";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		requestContextService.requireAdmin();
		PaginationDto paginationDto = new PaginationDto();
		paginationDto.setUserId(RequestHeaderUtil.getUserId());
		paginationDto.setSearchText(request == null ? null : request.getQ());
		List<WealthSnapshotEntity> rows = wealthSnapshotDao.findByUser(paginationDto);
		List<SnapshotDto> result = GenericAssembler.toDTOList(rows, SnapshotDto.class);
		// The assembler copies same-named fields only; the DTO's createdAt is the entity's crtdDt.
		for (int i = 0; i < rows.size(); i++) {
			result.get(i).setCreatedAt(rows.get(i).getCrtdDt());
		}
		LOGGER.info(WealthServiceConstants.EXITING + methodName + " | rows=" + result.size());
		return result;
	}

	private RoleDto role(String role, String label, boolean locked, List<String> menus) {
		PaginationDto paginationDto = new PaginationDto();
		paginationDto.setSearchText(role);
		RoleDto dto = new RoleDto();
		dto.setRole(role);
		dto.setLabel(label);
		dto.setUsers((int) wealthRoleDao.countUsersByRole(paginationDto));
		dto.setLocked(locked);
		dto.setMenus(menus);
		return dto;
	}

	// A role with nothing saved sees every menu; saved keys that are no longer known are dropped.
	private List<String> savedMenusOrAll(String role) {
		PaginationDto paginationDto = new PaginationDto();
		paginationDto.setSearchText(role);
		String csv = wealthUserDao.findMenusByRole(paginationDto);
		if (csv == null || csv.isBlank()) {
			return allKeys();
		}
		List<String> known = allKeys();
		return Arrays.stream(csv.split(",")).filter(known::contains).toList();
	}

	private static List<String> allKeys() {
		return Arrays.stream(WealthServiceConstants.MENUS).map(m -> m[0]).toList();
	}
}
