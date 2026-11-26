package com.wealth.bss.daoimplement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import com.wealth.bss.common.model.dto.PaginationDto;
import com.wealth.bss.common.model.entity.WealthAccountEntity;
import com.wealth.bss.constants.WealthServiceConstants;
import com.wealth.bss.query.QueryConstants;

class WealthAccountDaoImpleTest {

	private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
	private final WealthAccountDaoImple dao = new WealthAccountDaoImple(jdbc);

	@SuppressWarnings("unchecked")
	@Test
	void findByUserPassesTheCallerOnly() {
		WealthAccountEntity row = new WealthAccountEntity();
		when(jdbc.query(eq(QueryConstants.FIND_ACCOUNTS_BY_USER), any(RowMapper.class), eq("user-1"))).thenReturn(List.of(row));
		PaginationDto dto = new PaginationDto();
		dto.setUserId("user-1");
		assertEquals(1, dao.findByUser(dto).size());
	}

	@SuppressWarnings("unchecked")
	@Test
	void findByIdIsNullForAnotherUsersAccount() {
		when(jdbc.queryForObject(eq(QueryConstants.FIND_ACCOUNT_BY_ID), any(RowMapper.class), eq("user-1"), eq("acc-9")))
				.thenThrow(new EmptyResultDataAccessException(1));
		PaginationDto dto = new PaginationDto();
		dto.setUserId("user-1");
		dto.setId("acc-9");
		assertNull(dao.findById(dto));
	}

	@SuppressWarnings("unchecked")
	@Test
	void insertSetsTheActorFirstAndWritesTheSubtypeTwice() {
		WealthAccountEntity saved = new WealthAccountEntity();
		saved.setId("new");
		when(jdbc.query(eq(QueryConstants.INSERT_ACCOUNT), any(RowMapper.class), any(Object[].class))).thenReturn(List.of(saved));
		WealthAccountEntity input = new WealthAccountEntity();
		input.setUserId("user-1");
		input.setAccountKey("hdfc-savings-main");
		input.setBankName("HDFC");
		input.setAccountName("Main");
		input.setSubtypeCode("savings");
		input.setCurrency("INR");
		input.setFdBookedOn(new Date(0));

		assertEquals("new", dao.insert(input).getId());

		verify(jdbc).queryForObject(QueryConstants.SET_ACTOR, String.class, WealthServiceConstants.ACTOR_OWNER, "");
		ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
		verify(jdbc).query(eq(QueryConstants.INSERT_ACCOUNT), any(RowMapper.class), args.capture());
		assertEquals(13, args.getValue().length);
		assertEquals("savings", args.getValue()[4]);
		assertEquals("savings", args.getValue()[5]);
		assertEquals(java.sql.Date.class, args.getValue()[9].getClass());
		assertNull(args.getValue()[10]);
	}

	@SuppressWarnings("unchecked")
	@Test
	void insertReturnsNullWhenTheAccountKeyExists() {
		when(jdbc.query(eq(QueryConstants.INSERT_ACCOUNT), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
		assertNull(dao.insert(new WealthAccountEntity()));
	}

	@SuppressWarnings("unchecked")
	@Test
	void updateFdTermsUsesTheAgentActorWhenGiven() {
		WealthAccountEntity saved = new WealthAccountEntity();
		when(jdbc.query(eq(QueryConstants.UPDATE_ACCOUNT_FD_TERMS), any(RowMapper.class), any(Object[].class))).thenReturn(List.of(saved));
		WealthAccountEntity input = new WealthAccountEntity();
		input.setActor(WealthServiceConstants.ACTOR_AGENT);
		input.setUserId("user-1");
		input.setId("acc-1");
		input.setFdPrincipal(new BigDecimal("1000"));

		dao.updateFdTerms(input);

		verify(jdbc).queryForObject(QueryConstants.SET_ACTOR, String.class, WealthServiceConstants.ACTOR_AGENT, "");
		ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
		verify(jdbc).query(eq(QueryConstants.UPDATE_ACCOUNT_FD_TERMS), any(RowMapper.class), args.capture());
		assertEquals("user-1", args.getValue()[5]);
		assertEquals("acc-1", args.getValue()[6]);
	}

	@Test
	void everyAccountReadFiltersByTheUserAndSkipsTheLegacyCopies() {
		for (String sql : List.of(QueryConstants.FIND_ACCOUNTS_BY_USER, QueryConstants.FIND_ACCOUNT_BY_ID)) {
			assertTrue(sql.contains("WHERE USER_ID = CAST(? AS UUID)"));
			assertFalse(sql.contains("CURRENT_BALANCE"));
			assertFalse(sql.contains("BALANCE_AS_OF"));
			assertFalse(sql.contains("ACCOUNT_TYPE"));
		}
	}
}
