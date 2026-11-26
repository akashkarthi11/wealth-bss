package com.wealth.bss.serviceimplement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wealth.bss.common.model.dao.IfWealthMenuDao;
import com.wealth.bss.common.model.dao.IfWealthUserDao;
import com.wealth.bss.common.model.entity.WealthMenuEntity;
import com.wealth.common.model.dto.ThreadLocalDto;
import com.wealth.common.model.dto.menu.NavMenuDto;
import com.wealth.common.utils.generic.RequestHeaderUtil;

class MenusServiceImpleTest {

	private IfWealthMenuDao menus;
	private IfWealthUserDao users;
	private MenusServiceImple service;

	private WealthMenuEntity entry(String key, String parent, boolean adminOnly, boolean active, int order) {
		WealthMenuEntity e = new WealthMenuEntity();
		e.setMenuKey(key);
		e.setParentKey(parent);
		e.setLabel("L-" + key);
		e.setRoute("/" + key);
		e.setMenuGroup(parent == null ? "Money" : "Ignored");
		e.setSortOrder(order);
		e.setAdminOnly(adminOnly);
		e.setActive(active);
		return e;
	}

	@BeforeEach
	void setUp() {
		menus = mock(IfWealthMenuDao.class);
		users = mock(IfWealthUserDao.class);
		service = new MenusServiceImple(menus, users);
		when(menus.findAll()).thenReturn(List.of(entry("overview", null, false, true, 10), entry("banks", null, false, true, 20),
				entry("banks-statements", "banks", false, true, 21), entry("banks-cards", "banks", false, true, 22), entry("investments", null, false, false, 30),
				entry("records", null, true, true, 40), entry("settings", null, true, true, 50), entry("settings-menus", "settings", true, true, 51)));
	}

	@AfterEach
	void tearDown() {
		RequestHeaderUtil.clear();
	}

	private static final String ID = "3f2b8c1e-0a4d-4c55-9d0e-1b6c7a8d9e10";

	private com.wealth.common.client.NexusServiceClient nexus;

	@SuppressWarnings("unchecked")
	private void signedInAs(String role) {
		nexus = mock(com.wealth.common.client.NexusServiceClient.class);
		org.springframework.beans.factory.ObjectProvider<com.wealth.common.client.NexusServiceClient> provider = mock(org.springframework.beans.factory.ObjectProvider.class);
		when(provider.getIfAvailable()).thenReturn(nexus);
		new RequestHeaderUtil(provider);
		RequestHeaderUtil.setUserId(ID);
		ThreadLocalDto caller = new ThreadLocalDto();
		caller.setUserId(ID);
		caller.setRole(role);
		when(nexus.getRequestHeaderCache(ID)).thenReturn(caller);
	}

	private List<String> keys(List<NavMenuDto> nav) {
		return nav.stream().map(NavMenuDto::getKey).toList();
	}

	@Test
	void theAdminSeesEveryActiveEntryWithItsSubmenusInOrder() {
		signedInAs("admin");
		List<NavMenuDto> nav = service.navigation();
		assertEquals(List.of("overview", "banks", "records", "settings"), keys(nav));
		assertEquals(List.of("banks-statements", "banks-cards"), keys(nav.get(1).getChildren()));
		assertEquals(List.of("settings-menus"), keys(nav.get(3).getChildren()));
		assertEquals("/settings-menus", nav.get(3).getChildren().get(0).getRoute(), "the route comes from the table as it is");
		assertEquals("Money", nav.get(1).getChildren().get(0).getGroup(), "a submenu takes its menu's group");
	}

	@Test
	void aRoleWithNothingSavedSeesEveryActiveNonAdminEntry() {
		signedInAs("user");
		when(users.findMenusByRole(any())).thenReturn(null);
		List<NavMenuDto> nav = service.navigation();
		assertEquals(List.of("overview", "banks"), keys(nav));
		assertEquals(List.of("banks-statements", "banks-cards"), keys(nav.get(1).getChildren()));
	}

	@Test
	void aSavedRoleSeesOnlyWhatIsListedAndASubmenuNeedsItsOwnKey() {
		signedInAs("user");
		when(users.findMenusByRole(any())).thenReturn("overview,banks,banks-cards,investments,records,settings");
		List<NavMenuDto> nav = service.navigation();
		assertEquals(List.of("overview", "banks"), keys(nav), "investments is not active, records and settings are admin only");
		assertEquals(List.of("banks-cards"), keys(nav.get(1).getChildren()));
	}

	@Test
	void aSubmenuNeverShowsWithoutItsMenu() {
		signedInAs("user");
		when(users.findMenusByRole(any())).thenReturn("overview,banks-statements");
		List<NavMenuDto> nav = service.navigation();
		assertEquals(List.of("overview"), keys(nav));
		assertTrue(nav.get(0).getChildren().isEmpty());
	}

	@Test
	void anInactiveEntryIsHiddenForEveryoneAndNoCallerMeansNoAdminEntries() {
		signedInAs("admin");
		assertTrue(!keys(service.navigation()).contains("investments"));
		RequestHeaderUtil.clear();
		when(users.findMenusByRole(any())).thenReturn(null);
		assertEquals(List.of("overview", "banks"), keys(service.navigation()));
	}
}
