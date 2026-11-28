package com.wealth.bss.serviceimplement;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.wealth.bss.common.model.dao.IfWealthMenuDao;
import com.wealth.bss.common.model.dao.IfWealthUserDao;
import com.wealth.bss.common.model.dto.PaginationDto;
import com.wealth.bss.common.model.entity.WealthMenuEntity;
import com.fasterxml.jackson.core.type.TypeReference;
import com.wealth.bss.constants.WealthServiceConstants;
import com.wealth.bss.nexus.service.IfNexusReadService;
import com.wealth.bss.serviceinterface.IfMenusService;
import com.wealth.common.logger.KarthiLogger;
import com.wealth.common.model.dto.ThreadLocalDto;
import com.wealth.common.model.dto.menu.NavMenuDto;
import com.wealth.common.utils.generic.RequestHeaderUtil;

@Service
public class MenusServiceImple implements IfMenusService {

	private static final KarthiLogger LOGGER = new KarthiLogger(MenusServiceImple.class);

	private final IfWealthMenuDao wealthMenuDao;
	private final IfWealthUserDao wealthUserDao;
	private final IfNexusReadService nexusReadService;

	public MenusServiceImple(IfWealthMenuDao wealthMenuDao, IfWealthUserDao wealthUserDao, IfNexusReadService nexusReadService) {
		this.wealthMenuDao = wealthMenuDao;
		this.wealthUserDao = wealthUserDao;
		this.nexusReadService = nexusReadService;
	}

	@Override
	public List<NavMenuDto> navigation() {
		String methodName = "navigation";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		ThreadLocalDto caller = RequestHeaderUtil.getThreadLocalDto();
		// the menus depend only on the role, so one copy per role is kept for a short while; saving a role's menus drops it
		if (caller == null || caller.getRole() == null) {
			return build(caller);
		}
		List<NavMenuDto> remembered = nexusReadService.rememberShared(WealthServiceConstants.NEXUS_READ_NAV, caller.getRole(), Duration.ofSeconds(WealthServiceConstants.NEXUS_MEMO_NAV_SECONDS),
				new TypeReference<List<NavMenuDto>>() {
				}, () -> build(caller));
		LOGGER.info(WealthServiceConstants.EXITING + methodName + " | menus=" + remembered.size());
		return remembered;
	}

	private List<NavMenuDto> build(ThreadLocalDto caller) {
		String methodName = "navigation";
		boolean admin = caller != null && WealthServiceConstants.ROLE_ADMIN.equals(caller.getRole());
		Set<String> allowed = admin || caller == null ? null : permissionsOf(caller.getRole());
		List<WealthMenuEntity> all = wealthMenuDao.findAll();
		List<NavMenuDto> result = new ArrayList<>();
		for (WealthMenuEntity top : all) {
			if (top.getParentKey() != null || !visible(top, admin, allowed)) {
				continue;
			}
			NavMenuDto menu = toDto(top, top.getMenuGroup());
			List<NavMenuDto> children = new ArrayList<>();
			for (WealthMenuEntity child : all) {
				if (top.getMenuKey().equals(child.getParentKey()) && visible(child, admin, allowed)) {
					children.add(toDto(child, top.getMenuGroup()));
				}
			}
			menu.setChildren(children);
			result.add(menu);
		}
		LOGGER.info(WealthServiceConstants.EXITING + methodName + " | menus=" + result.size());
		return result;
	}

	// null = every entry (nothing saved for the role).
	private Set<String> permissionsOf(String role) {
		PaginationDto paginationDto = new PaginationDto();
		paginationDto.setSearchText(role);
		String csv = wealthUserDao.findMenusByRole(paginationDto);
		return csv == null || csv.isBlank() ? null : new HashSet<>(Arrays.asList(csv.split(",")));
	}

	private boolean visible(WealthMenuEntity entry, boolean admin, Set<String> allowed) {
		if (!entry.isActive()) {
			return false;
		}
		return admin || (!entry.isAdminOnly() && (allowed == null || allowed.contains(entry.getMenuKey())));
	}

	private NavMenuDto toDto(WealthMenuEntity entry, String group) {
		NavMenuDto dto = new NavMenuDto();
		dto.setKey(entry.getMenuKey());
		dto.setLabel(entry.getLabel());
		dto.setRoute(entry.getRoute());
		dto.setGroup(group);
		dto.setChildren(new ArrayList<>());
		return dto;
	}
}
