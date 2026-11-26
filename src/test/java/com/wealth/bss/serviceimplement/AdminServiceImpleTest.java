package com.wealth.bss.serviceimplement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.wealth.bss.common.model.dao.IfWealthRoleDao;
import com.wealth.bss.common.model.dao.IfWealthSnapshotDao;
import com.wealth.bss.common.model.dao.IfWealthUserDao;
import com.wealth.bss.common.model.dto.PaginationDto;
import com.wealth.bss.common.model.entity.WealthRoleMenuEntity;
import com.wealth.bss.common.model.entity.WealthSnapshotEntity;
import com.wealth.bss.serviceinterface.IfRequestContextService;
import com.wealth.common.generic.BaseError;
import com.wealth.common.generic.KarthiException;
import com.wealth.common.model.dto.admin.RecordsRequestDto;
import com.wealth.common.model.dto.admin.RoleDto;
import com.wealth.common.model.dto.admin.RoleMenusDto;
import com.wealth.common.model.dto.admin.RoleMenusRequestDto;
import com.wealth.common.model.dto.admin.RolesOverviewDto;
import com.wealth.common.model.dto.admin.SnapshotDto;
import com.wealth.common.utils.generic.RequestHeaderUtil;

class AdminServiceImpleTest {

	private static final String ADMIN_ID = "3f2b8c1e-0a4d-4c55-9d0e-1b6c7a8d9e10";

	private IfRequestContextService context;
	private IfWealthUserDao users;
	private IfWealthRoleDao roles;
	private IfWealthSnapshotDao snapshots;
	private AdminServiceImple service;

	@BeforeEach
	void setUp() {
		context = mock(IfRequestContextService.class);
		users = mock(IfWealthUserDao.class);
		roles = mock(IfWealthRoleDao.class);
		snapshots = mock(IfWealthSnapshotDao.class);
		service = new AdminServiceImple(context, users, roles, snapshots);
		RequestHeaderUtil.setUserId(ADMIN_ID);
	}

	@AfterEach
	void tearDown() {
		RequestHeaderUtil.clear();
	}

	private String codeOf(Runnable call) {
		return assertThrows(KarthiException.class, call::run).getMessage();
	}

	private RoleMenusRequestDto menus(String role, String... keys) {
		RoleMenusRequestDto r = new RoleMenusRequestDto();
		r.setRole(role);
		r.setMenus(keys == null ? null : List.of(keys));
		return r;
	}

	@Test
	void everyActionIsAdminOnly() {
		doThrow(new KarthiException(BaseError.ADMIN_ONLY_ACTION)).when(context).requireAdmin();
		assertEquals(BaseError.ADMIN_ONLY_ACTION, codeOf(service::rolesOverview));
		assertEquals(BaseError.ADMIN_ONLY_ACTION, codeOf(() -> service.updateRoleMenus(menus("user", "overview"))));
		assertEquals(BaseError.ADMIN_ONLY_ACTION, codeOf(() -> service.listRecords(null)));
		verify(roles, never()).saveMenus(any());
	}

	@Test
	void overviewHasTwelveMenusAndTwoRolesWithAdminLocked() {
		when(roles.countUsersByRole(any())).thenReturn(2L);
		when(users.findMenusByRole(any())).thenReturn("overview,banks,unknown-key");

		RolesOverviewDto overview = service.rolesOverview();

		assertEquals(12, overview.getMenus().size());
		RoleDto admin = overview.getRoles().get(0);
		RoleDto user = overview.getRoles().get(1);
		assertTrue(admin.isLocked());
		assertEquals(12, admin.getMenus().size());
		assertEquals(2, admin.getUsers());
		assertEquals(List.of("overview", "banks"), user.getMenus());
	}

	@Test
	void aRoleWithNothingSavedSeesEveryMenu() {
		when(users.findMenusByRole(any())).thenReturn(null);
		assertEquals(12, service.rolesOverview().getRoles().get(1).getMenus().size());
	}

	@Test
	void updateSavesTheKnownMenusInMenuOrderAndRecordsTheEditor() {
		RoleMenusDto saved = service.updateRoleMenus(menus("user", "banks", "overview", "banks"));

		assertEquals(List.of("overview", "banks"), saved.getMenus());
		ArgumentCaptor<WealthRoleMenuEntity> entity = ArgumentCaptor.forClass(WealthRoleMenuEntity.class);
		verify(roles).saveMenus(entity.capture());
		assertEquals("overview,banks", entity.getValue().getMenus());
		assertEquals("user", entity.getValue().getRole());
		assertEquals(ADMIN_ID, entity.getValue().getUpdatedBy());
	}

	@Test
	void updateRefusesTheAdminRoleAnUnknownRoleNoMenusAndAnUnknownMenu() {
		assertEquals(BaseError.ROLE_LOCKED, codeOf(() -> service.updateRoleMenus(menus("admin", "overview"))));
		assertEquals(BaseError.UNKNOWN_ROLE, codeOf(() -> service.updateRoleMenus(menus("guest", "overview"))));
		assertEquals(BaseError.CHOOSE_A_MENU, codeOf(() -> service.updateRoleMenus(menus("user"))));
		assertEquals(BaseError.CHOOSE_A_MENU, codeOf(() -> service.updateRoleMenus(menus("user", (String[]) null))));
		assertEquals(BaseError.UNKNOWN_MENU, codeOf(() -> service.updateRoleMenus(menus("user", "overview", "secrets"))));
		assertEquals(BaseError.INVALID_REQUEST, codeOf(() -> service.updateRoleMenus(null)));
		verify(roles, never()).saveMenus(any());
	}

	@Test
	void recordsAreTheCallersOwnWithTheSearchAndTheCreatedTime() {
		WealthSnapshotEntity row = new WealthSnapshotEntity();
		row.setId("s1");
		row.setSourceName("Slice statement");
		row.setCrtdDt(new Timestamp(5000));
		when(snapshots.findByUser(any())).thenReturn(List.of(row));
		RecordsRequestDto request = new RecordsRequestDto();
		request.setQ("slice");

		List<SnapshotDto> result = service.listRecords(request);

		ArgumentCaptor<PaginationDto> page = ArgumentCaptor.forClass(PaginationDto.class);
		verify(snapshots).findByUser(page.capture());
		assertEquals(ADMIN_ID, page.getValue().getUserId());
		assertEquals("slice", page.getValue().getSearchText());
		assertEquals("Slice statement", result.get(0).getSourceName());
		assertEquals(new Timestamp(5000), result.get(0).getCreatedAt());
	}
}
