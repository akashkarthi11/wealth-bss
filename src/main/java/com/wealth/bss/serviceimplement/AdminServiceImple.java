package com.wealth.bss.serviceimplement;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.wealth.bss.common.model.dao.IfWealthMenuDao;
import com.wealth.bss.common.model.dao.IfWealthRoleDao;
import com.wealth.bss.common.model.entity.WealthMenuEntity;
import com.wealth.bss.common.model.entity.WealthRoleEntity;
import com.wealth.bss.common.model.entity.WealthUserEntity;
import com.wealth.bss.common.model.dao.IfWealthSnapshotDao;
import com.wealth.bss.common.model.dao.IfWealthUserDao;
import com.wealth.bss.common.model.dto.PaginationDto;
import com.wealth.bss.common.model.entity.WealthRoleMenuEntity;
import com.wealth.bss.common.model.entity.WealthSnapshotEntity;
import com.wealth.bss.constants.WealthServiceConstants;
import com.wealth.bss.nexus.service.IfNexusReadService;
import com.wealth.bss.serviceinterface.IfAdminService;
import com.wealth.bss.serviceinterface.IfRequestContextService;
import com.wealth.common.assembler.GenericAssembler;
import com.wealth.common.generic.BaseError;
import com.wealth.common.generic.KarthiException;
import com.wealth.common.logger.KarthiLogger;
import com.wealth.common.model.dto.admin.MenuDto;
import com.wealth.common.model.dto.admin.RecordsRequestDto;
import com.wealth.common.model.dto.admin.RoleCreateRequestDto;
import com.wealth.common.model.dto.admin.UserAccessDto;
import com.wealth.common.model.dto.admin.UserRoleRequestDto;
import com.wealth.common.model.dto.admin.RoleDto;
import com.wealth.common.model.dto.admin.RoleMenusDto;
import com.wealth.common.model.dto.admin.RolesOverviewDto;
import com.wealth.common.model.dto.admin.SnapshotDto;
import com.wealth.common.utils.generic.RequestHeaderUtil;

// Rules carried over from the Node roles route: two fixed roles, admin always sees every menu, the user role gets at least one known menu.
@Service
public class AdminServiceImple implements IfAdminService {

	private static final KarthiLogger LOGGER = new KarthiLogger(AdminServiceImple.class);
	private static final Pattern ROLE_KEY = Pattern.compile("^[a-z0-9-]{2,30}$");
	private static final Pattern USER_ID = Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

	private final IfRequestContextService requestContextService;
	private final IfWealthUserDao wealthUserDao;
	private final IfWealthRoleDao wealthRoleDao;
	private final IfWealthSnapshotDao wealthSnapshotDao;
	private final IfWealthMenuDao wealthMenuDao;
	private final IfNexusReadService nexusReadService;

	public AdminServiceImple(IfRequestContextService requestContextService, IfWealthUserDao wealthUserDao, IfWealthRoleDao wealthRoleDao,
			IfWealthSnapshotDao wealthSnapshotDao, IfWealthMenuDao wealthMenuDao, IfNexusReadService nexusReadService) {
		this.nexusReadService = nexusReadService;
		this.requestContextService = requestContextService;
		this.wealthUserDao = wealthUserDao;
		this.wealthRoleDao = wealthRoleDao;
		this.wealthSnapshotDao = wealthSnapshotDao;
		this.wealthMenuDao = wealthMenuDao;
	}

	@Override
	public RolesOverviewDto rolesOverview() {
		String methodName = "rolesOverview";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		requestContextService.requireAdmin();
		List<MenuDto> menus = new ArrayList<>();
		for (WealthMenuEntity entry : inOrder()) {
			MenuDto dto = new MenuDto();
			dto.setKey(entry.getMenuKey());
			dto.setLabel(entry.getLabel());
			dto.setGroup(entry.getMenuGroup());
			dto.setParentKey(entry.getParentKey());
			dto.setActive(entry.isActive());
			dto.setAdminOnly(entry.isAdminOnly() || parentIsAdminOnly(entry));
			menus.add(dto);
		}
		RolesOverviewDto overview = new RolesOverviewDto();
		overview.setMenus(menus);
		List<RoleDto> roles = new ArrayList<>();
		for (WealthRoleEntity entry : wealthRoleDao.findAllRoles()) {
			roles.add(role(entry.getRole(), entry.getLabel(), entry.isLocked(), entry.isLocked() ? List.of() : savedMenusOrAll(entry.getRole())));
		}
		overview.setRoles(roles);
		LOGGER.info(WealthServiceConstants.EXITING + methodName);
		return overview;
	}

	@Override
	public RoleMenusDto updateRoleMenus(RoleMenusDto request) {
		String methodName = "updateRoleMenus";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		requestContextService.requireAdmin();
		if (request == null || request.getRole() == null) {
			throw new KarthiException(BaseError.INVALID_REQUEST);
		}
		WealthRoleEntity known = findRole(request.getRole());
		if (known == null) {
			throw new KarthiException(BaseError.UNKNOWN_ROLE);
		}
		if (known.isLocked()) {
			throw new KarthiException(BaseError.ROLE_LOCKED);
		}
		if (request.getMenus() == null || request.getMenus().isEmpty()) {
			throw new KarthiException(BaseError.CHOOSE_A_MENU);
		}
		List<WealthMenuEntity> entries = assignable();
		List<String> keys = entries.stream().map(WealthMenuEntity::getMenuKey).toList();
		if (!keys.containsAll(request.getMenus())) {
			throw new KarthiException(BaseError.UNKNOWN_MENU);
		}
		// A submenu cannot be on without the menu it belongs to.
		for (WealthMenuEntity entry : entries) {
			if (request.getMenus().contains(entry.getMenuKey()) && entry.getParentKey() != null && !request.getMenus().contains(entry.getParentKey())) {
				throw new KarthiException(BaseError.MENU_NEEDS_PARENT);
			}
		}
		// Saved in the order of the menu list, without repeats.
		List<String> clean = keys.stream().filter(request.getMenus()::contains).toList();
		WealthRoleMenuEntity entity = new WealthRoleMenuEntity();
		entity.setRole(request.getRole());
		entity.setMenus(String.join(",", clean));
		entity.setUpdatedBy(RequestHeaderUtil.getUserId());
		wealthRoleDao.saveMenus(entity);
		nexusReadService.evictShared(WealthServiceConstants.NEXUS_READ_NAV, request.getRole());
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

	@Override
	public RoleDto createRole(RoleCreateRequestDto request) {
		String methodName = "createRole";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		requestContextService.requireAdmin();
		String label = request == null || request.getLabel() == null ? "" : request.getLabel().trim();
		String key = keyFrom(label);
		if (label.isEmpty() || label.length() > WealthServiceConstants.ROLE_LABEL_MAX_LENGTH || !ROLE_KEY.matcher(key).matches()) {
			throw new KarthiException(BaseError.ROLE_NAME_INVALID);
		}
		WealthRoleEntity entity = new WealthRoleEntity();
		entity.setRole(key);
		entity.setLabel(label);
		if (wealthRoleDao.insertRole(entity) == null) {
			throw new KarthiException(BaseError.ROLE_EXISTS);
		}
		// Least privilege: a new role starts with Overview only (or the first menu a role can have); the admin ticks the rest.
		List<String> start = startingMenus();
		WealthRoleMenuEntity menus = new WealthRoleMenuEntity();
		menus.setRole(key);
		menus.setMenus(String.join(",", start));
		menus.setUpdatedBy(RequestHeaderUtil.getUserId());
		wealthRoleDao.saveMenus(menus);
		LOGGER.info(WealthServiceConstants.EXITING + methodName);
		return role(key, label, false, start);
	}

	@Override
	public List<UserAccessDto> listUsers() {
		requestContextService.requireAdmin();
		return wealthUserDao.findAll().stream().map(this::toAccess).toList();
	}

	@Override
	public UserAccessDto setUserRole(UserRoleRequestDto request) {
		String methodName = "setUserRole";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		requestContextService.requireAdmin();
		if (request == null || request.getUserId() == null || request.getRole() == null || !USER_ID.matcher(request.getUserId()).matches()) {
			throw new KarthiException(BaseError.INVALID_REQUEST);
		}
		if (findRole(request.getRole()) == null) {
			throw new KarthiException(BaseError.UNKNOWN_ROLE);
		}
		if (request.getUserId().equalsIgnoreCase(RequestHeaderUtil.getUserId())) {
			throw new KarthiException(BaseError.OWN_ROLE);
		}
		PaginationDto lookup = new PaginationDto();
		lookup.setId(request.getUserId());
		WealthUserEntity user = wealthUserDao.findById(lookup);
		if (user == null) {
			throw new KarthiException(BaseError.USER_NOT_FOUND);
		}
		if (WealthServiceConstants.ROLE_ADMIN.equals(user.getRole()) && !WealthServiceConstants.ROLE_ADMIN.equals(request.getRole())) {
			PaginationDto admins = new PaginationDto();
			admins.setSearchText(WealthServiceConstants.ROLE_ADMIN);
			if (wealthRoleDao.countUsersByRole(admins) <= 1) {
				throw new KarthiException(BaseError.LAST_ADMIN);
			}
		}
		WealthUserEntity change = new WealthUserEntity();
		change.setId(user.getId());
		change.setRole(request.getRole());
		WealthUserEntity saved = wealthUserDao.updateRole(change);
		if (saved == null) {
			throw new KarthiException(BaseError.USER_NOT_FOUND);
		}
		requestContextService.evict(saved.getId());
		LOGGER.info(WealthServiceConstants.EXITING + methodName);
		return toAccess(saved);
	}

	private UserAccessDto toAccess(WealthUserEntity user) {
		UserAccessDto dto = new UserAccessDto();
		dto.setId(user.getId());
		dto.setEmail(user.getEmail());
		dto.setRole(user.getRole());
		return dto;
	}

	private WealthRoleEntity findRole(String role) {
		return wealthRoleDao.findAllRoles().stream().filter(entry -> entry.getRole().equals(role)).findFirst().orElse(null);
	}

	// "Family members" to "family-members": lower case letters and digits, one dash between words.
	private static String keyFrom(String label) {
		String key = label.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
		return key.length() > WealthServiceConstants.ROLE_KEY_MAX_LENGTH ? key.substring(0, WealthServiceConstants.ROLE_KEY_MAX_LENGTH).replaceAll("-+$", "") : key;
	}

	private List<String> startingMenus() {
		List<String> keys = allKeys();
		if (keys.contains(WealthServiceConstants.STARTING_MENU)) {
			return List.of(WealthServiceConstants.STARTING_MENU);
		}
		return keys.isEmpty() ? List.of() : List.of(keys.get(0));
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

	private List<String> allKeys() {
		return assignable().stream().map(WealthMenuEntity::getMenuKey).toList();
	}

	// Every entry, each top-level menu followed by its submenus: what the permission screen shows (admin-only ones are marked and cannot be ticked).
	private List<WealthMenuEntity> inOrder() {
		List<WealthMenuEntity> all = wealthMenuDao.findAll();
		List<WealthMenuEntity> result = new ArrayList<>();
		for (WealthMenuEntity top : all) {
			if (top.getParentKey() != null) {
				continue;
			}
			result.add(top);
			for (WealthMenuEntity child : all) {
				if (top.getMenuKey().equals(child.getParentKey())) {
					result.add(child);
				}
			}
		}
		return result;
	}

	// A submenu of an admin-only menu is admin only too.
	private boolean parentIsAdminOnly(WealthMenuEntity entry) {
		return entry.getParentKey() != null && wealthMenuDao.findAll().stream().anyMatch(top -> top.getMenuKey().equals(entry.getParentKey()) && top.isAdminOnly());
	}

	// The entries a role can be given, from the table: not admin only (the admin always sees those), each top-level menu followed by its submenus.
	private List<WealthMenuEntity> assignable() {
		List<WealthMenuEntity> all = wealthMenuDao.findAll();
		List<WealthMenuEntity> result = new ArrayList<>();
		for (WealthMenuEntity top : all) {
			if (top.getParentKey() != null || top.isAdminOnly()) {
				continue;
			}
			result.add(top);
			for (WealthMenuEntity child : all) {
				if (top.getMenuKey().equals(child.getParentKey()) && !child.isAdminOnly()) {
					result.add(child);
				}
			}
		}
		return result;
	}
}
