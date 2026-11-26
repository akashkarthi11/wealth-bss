package com.wealth.bss.daoimplement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import com.wealth.bss.common.model.dto.PaginationDto;
import com.wealth.bss.common.model.entity.WealthRoleMenuEntity;
import com.wealth.bss.constants.WealthServiceConstants;
import com.wealth.bss.query.QueryConstants;

class WealthRoleAndSnapshotDaoImpleTest {

	private final JdbcTemplate jdbc = mock(JdbcTemplate.class);

	@Test
	void countUsersByRoleReadsTheRoleFromSearchText() {
		when(jdbc.queryForObject(QueryConstants.COUNT_USERS_BY_ROLE, Long.class, "admin")).thenReturn(2L);
		PaginationDto dto = new PaginationDto();
		dto.setSearchText("admin");
		assertEquals(2L, new WealthRoleDaoImple(jdbc).countUsersByRole(dto));
	}

	@SuppressWarnings("unchecked")
	@Test
	void saveMenusSetsTheOwnerActorByDefaultAndPassesTheMenuList() {
		WealthRoleMenuEntity saved = new WealthRoleMenuEntity();
		saved.setRole("user");
		when(jdbc.query(eq(QueryConstants.UPSERT_ROLE_MENUS), any(RowMapper.class), any(Object[].class))).thenReturn(List.of(saved));
		WealthRoleMenuEntity input = new WealthRoleMenuEntity();
		input.setRole("user");
		input.setMenus("overview,banks");
		input.setUpdatedBy("admin-1");

		assertEquals("user", new WealthRoleDaoImple(jdbc).saveMenus(input).getRole());

		verify(jdbc).queryForObject(QueryConstants.SET_ACTOR, String.class, WealthServiceConstants.ACTOR_OWNER, "");
		ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
		verify(jdbc).query(eq(QueryConstants.UPSERT_ROLE_MENUS), any(RowMapper.class), args.capture());
		assertEquals("overview,banks", args.getValue()[1]);
		assertEquals("admin-1", args.getValue()[2]);
	}

	@SuppressWarnings("unchecked")
	@Test
	void snapshotSearchIsWrappedAndEscapedAndBlankMeansNoFilter() {
		when(jdbc.query(eq(QueryConstants.FIND_SNAPSHOTS_BY_USER), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
		WealthSnapshotDaoImple dao = new WealthSnapshotDaoImple(jdbc);
		PaginationDto dto = new PaginationDto();
		dto.setUserId("user-1");
		dto.setSearchText("50%_off");
		dao.findByUser(dto);
		dto.setSearchText("  ");
		dao.findByUser(dto);

		ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
		verify(jdbc, org.mockito.Mockito.times(2)).query(eq(QueryConstants.FIND_SNAPSHOTS_BY_USER), any(RowMapper.class), args.capture());
		assertEquals("user-1", args.getAllValues().get(0)[0]);
		assertEquals("%50\\%\\_off%", args.getAllValues().get(0)[1]);
		assertNull(args.getAllValues().get(1)[1]);
	}
}
