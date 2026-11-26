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
import com.wealth.common.model.dto.admin.RolesOverviewDto;
import com.wealth.common.model.dto.admin.SnapshotDto;
import com.wealth.common.utils.generic.RequestHeaderUtil;

class AdminServiceImpleTest {

	private static final String ADMIN_ID = "3f2b8c1e-0a4d-4c55-9d0e-1b6c7a8d9e10";

	private IfRequestContextService context;
	private IfWealthUserDao users;
	private IfWealthRoleDao roles;
	private IfWealthSnapshotDao snapshots;
	private com.wealth.bss.common.model.dao.IfWealthMenuDao menuDao;
	private AdminServiceImple service;

	// A small menu table: four entries a role can be given (one is a submenu of banks) and two the admin always has.
	private com.wealth.bss.common.model.entity.WealthMenuEntity entry(String key, String parent, boolean adminOnly, boolean active, int order) {
		com.wealth.bss.common.model.entity.WealthMenuEntity e = new com.wealth.bss.common.model.entity.WealthMenuEntity();
		e.setMenuKey(key);
		e.setParentKey(parent);
		e.setLabel(key);
		e.setRoute("/" + key);
		e.setMenuGroup("Money");
		e.setSortOrder(order);
		e.setAdminOnly(adminOnly);
		e.setActive(active);
		return e;
	}

	private com.wealth.bss.common.model.entity.WealthRoleEntity role(String key, String label, boolean locked) {
		com.wealth.bss.common.model.entity.WealthRoleEntity r = new com.wealth.bss.common.model.entity.WealthRoleEntity();
		r.setRole(key);
		r.setLabel(label);
		r.setLocked(locked);
		return r;
	}

	@BeforeEach
	void setUp() {
		context = mock(IfRequestContextService.class);
		users = mock(IfWealthUserDao.class);
		roles = mock(IfWealthRoleDao.class);
		snapshots = mock(IfWealthSnapshotDao.class);
		menuDao = mock(com.wealth.bss.common.model.dao.IfWealthMenuDao.class);
		when(menuDao.findAll()).thenReturn(List.of(entry("overview", null, false, true, 10), entry("banks", null, false, true, 20),
				entry("banks-statements", "banks", false, false, 25), entry("investments", null, false, false, 30), entry("records", null, true, true, 40),
				entry("settings", null, true, true, 50), entry("settings-menus", "settings", true, true, 60)));
		when(roles.findAllRoles()).thenReturn(List.of(role("admin", "Admin", true), role("user", "User", false), role("family", "Family", false)));
		service = new AdminServiceImple(context, users, roles, snapshots, menuDao);
		RequestHeaderUtil.setUserId(ADMIN_ID);
	}

	@AfterEach
	void tearDown() {
		RequestHeaderUtil.clear();
	}

	private String codeOf(Runnable call) {
		return assertThrows(KarthiException.class, call::run).getMessage();
	}

	private RoleMenusDto menus(String role, String... keys) {
		RoleMenusDto r = new RoleMenusDto();
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
	void overviewListsEveryMenuFromTheTableWithSubmenusUnderTheirMenuAndMarksAdminOnlyOnes() {
		when(roles.countUsersByRole(any())).thenReturn(2L);
		when(users.findMenusByRole(any())).thenReturn("overview,banks,unknown-key");

		RolesOverviewDto overview = service.rolesOverview();

		assertEquals(List.of("overview", "banks", "banks-statements", "investments", "records", "settings", "settings-menus"), overview.getMenus().stream().map(m -> m.getKey()).toList());
		assertEquals(List.of(false, false, false, false, true, true, true), overview.getMenus().stream().map(m -> m.isAdminOnly()).toList());
		assertEquals("banks", overview.getMenus().get(2).getParentKey());
		assertTrue(!overview.getMenus().get(2).isActive() && overview.getMenus().get(0).isActive());
		assertEquals(List.of("admin", "user", "family"), overview.getRoles().stream().map(r -> r.getRole()).toList());
		assertEquals("Family", overview.getRoles().get(2).getLabel());
		RoleDto admin = overview.getRoles().get(0);
		RoleDto user = overview.getRoles().get(1);
		assertTrue(admin.isLocked());
		assertTrue(admin.getMenus().isEmpty());
		assertEquals(2, admin.getUsers());
		assertEquals(List.of("overview", "banks"), user.getMenus());
	}

	@Test
	void aRoleWithNothingSavedSeesEveryMenu() {
		when(users.findMenusByRole(any())).thenReturn(null);
		assertEquals(List.of("overview", "banks", "banks-statements", "investments"), service.rolesOverview().getRoles().get(1).getMenus());
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
		assertEquals(BaseError.UNKNOWN_MENU, codeOf(() -> service.updateRoleMenus(menus("user", "overview", "records"))), "admin only entries cannot be given");
		assertEquals(BaseError.UNKNOWN_MENU, codeOf(() -> service.updateRoleMenus(menus("user", "settings-menus"))));
		assertEquals(BaseError.INVALID_REQUEST, codeOf(() -> service.updateRoleMenus(null)));
		verify(roles, never()).saveMenus(any());
	}

	@Test
	void aSubmenuCanBeGivenOnlyTogetherWithItsMenu() {
		assertEquals(BaseError.MENU_NEEDS_PARENT, codeOf(() -> service.updateRoleMenus(menus("user", "overview", "banks-statements"))));
		verify(roles, never()).saveMenus(any());

		RoleMenusDto saved = service.updateRoleMenus(menus("user", "banks-statements", "banks"));
		assertEquals(List.of("banks", "banks-statements"), saved.getMenus());
	}

	@Test
	void menusCanBeChosenForAnyUnlockedRoleThatExists() {
		RoleMenusDto saved = service.updateRoleMenus(menus("family", "overview", "banks"));
		assertEquals(List.of("overview", "banks"), saved.getMenus());
		assertEquals(BaseError.UNKNOWN_ROLE, codeOf(() -> service.updateRoleMenus(menus("ghost", "overview"))));
		assertEquals(BaseError.ROLE_LOCKED, codeOf(() -> service.updateRoleMenus(menus("admin", "overview"))));
	}

	private com.wealth.common.model.dto.admin.RoleCreateRequestDto named(String label) {
		com.wealth.common.model.dto.admin.RoleCreateRequestDto r = new com.wealth.common.model.dto.admin.RoleCreateRequestDto();
		r.setLabel(label);
		return r;
	}

	@Test
	void aNewRoleGetsAKeyFromItsNameStartsWithOverviewOnlyAndIsRefusedWhenTheNameIsBadOrTaken() {
		when(roles.insertRole(any())).thenAnswer(i -> i.getArgument(0));

		RoleDto created = service.createRole(named("  Family Members! "));

		assertEquals("family-members", created.getRole());
		assertEquals("Family Members!", created.getLabel());
		assertEquals(List.of("overview"), created.getMenus());
		assertTrue(!created.isLocked());
		ArgumentCaptor<WealthRoleMenuEntity> menusSaved = ArgumentCaptor.forClass(WealthRoleMenuEntity.class);
		verify(roles).saveMenus(menusSaved.capture());
		assertEquals("family-members", menusSaved.getValue().getRole());
		assertEquals("overview", menusSaved.getValue().getMenus());

		for (String bad : new String[] { null, "", "   ", "!", "x", "a".repeat(41) }) {
			assertEquals(BaseError.ROLE_NAME_INVALID, codeOf(() -> service.createRole(named(bad))), String.valueOf(bad));
		}
		assertEquals(BaseError.ROLE_NAME_INVALID, codeOf(() -> service.createRole(null)));

		when(roles.insertRole(any())).thenReturn(null);
		assertEquals(BaseError.ROLE_EXISTS, codeOf(() -> service.createRole(named("User"))));
	}

	@Test
	void addRoleAndTheUsersActionsAreAdminOnly() {
		doThrow(new KarthiException(BaseError.ADMIN_ONLY_ACTION)).when(context).requireAdmin();
		assertEquals(BaseError.ADMIN_ONLY_ACTION, codeOf(() -> service.createRole(named("Family"))));
		assertEquals(BaseError.ADMIN_ONLY_ACTION, codeOf(service::listUsers));
		assertEquals(BaseError.ADMIN_ONLY_ACTION, codeOf(() -> service.setUserRole(roleChange("3f2b8c1e-0a4d-4c55-9d0e-1b6c7a8d9e11", "user"))));
		verify(roles, never()).insertRole(any());
		verify(users, never()).updateRole(any());
	}

	private static final String OTHER_ID = "3f2b8c1e-0a4d-4c55-9d0e-1b6c7a8d9e11";

	private com.wealth.common.model.dto.admin.UserRoleRequestDto roleChange(String userId, String role) {
		com.wealth.common.model.dto.admin.UserRoleRequestDto r = new com.wealth.common.model.dto.admin.UserRoleRequestDto();
		r.setUserId(userId);
		r.setRole(role);
		return r;
	}

	private com.wealth.bss.common.model.entity.WealthUserEntity user(String id, String email, String role) {
		com.wealth.bss.common.model.entity.WealthUserEntity u = new com.wealth.bss.common.model.entity.WealthUserEntity();
		u.setId(id);
		u.setEmail(email);
		u.setRole(role);
		return u;
	}

	@Test
	void theUsersListShowsEmailAndRoleOnly() {
		when(users.findAll()).thenReturn(List.of(user(ADMIN_ID, "a@example.com", "admin"), user(OTHER_ID, "b@example.com", "user")));
		List<com.wealth.common.model.dto.admin.UserAccessDto> list = service.listUsers();
		assertEquals(List.of("a@example.com", "b@example.com"), list.stream().map(u -> u.getEmail()).toList());
		assertEquals(List.of("admin", "user"), list.stream().map(u -> u.getRole()).toList());
	}

	@Test
	void aUsersRoleIsChangedAndTheyAreSignedOut() {
		when(users.findById(any())).thenReturn(user(OTHER_ID, "b@example.com", "user"));
		when(users.updateRole(any())).thenReturn(user(OTHER_ID, "b@example.com", "family"));

		com.wealth.common.model.dto.admin.UserAccessDto saved = service.setUserRole(roleChange(OTHER_ID, "family"));

		assertEquals("family", saved.getRole());
		ArgumentCaptor<com.wealth.bss.common.model.entity.WealthUserEntity> change = ArgumentCaptor.forClass(com.wealth.bss.common.model.entity.WealthUserEntity.class);
		verify(users).updateRole(change.capture());
		assertEquals(OTHER_ID, change.getValue().getId());
		assertEquals("family", change.getValue().getRole());
		verify(context).evict(OTHER_ID);
	}

	@Test
	void roleChangesAreRefusedForYourselfTheLastAdminUnknownRolesAndBadIds() {
		assertEquals(BaseError.OWN_ROLE, codeOf(() -> service.setUserRole(roleChange(ADMIN_ID, "user"))));
		assertEquals(BaseError.UNKNOWN_ROLE, codeOf(() -> service.setUserRole(roleChange(OTHER_ID, "ghost"))));
		assertEquals(BaseError.INVALID_REQUEST, codeOf(() -> service.setUserRole(roleChange("not-an-id", "user"))));
		assertEquals(BaseError.INVALID_REQUEST, codeOf(() -> service.setUserRole(null)));

		when(users.findById(any())).thenReturn(null);
		assertEquals(BaseError.USER_NOT_FOUND, codeOf(() -> service.setUserRole(roleChange(OTHER_ID, "user"))));

		when(users.findById(any())).thenReturn(user(OTHER_ID, "b@example.com", "admin"));
		when(roles.countUsersByRole(any())).thenReturn(1L);
		assertEquals(BaseError.LAST_ADMIN, codeOf(() -> service.setUserRole(roleChange(OTHER_ID, "user"))));
		verify(users, never()).updateRole(any());

		when(roles.countUsersByRole(any())).thenReturn(2L);
		when(users.updateRole(any())).thenReturn(user(OTHER_ID, "b@example.com", "user"));
		service.setUserRole(roleChange(OTHER_ID, "user"));
		verify(users).updateRole(any());
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
