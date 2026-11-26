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
import java.sql.Timestamp;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import com.wealth.bss.common.model.dto.PaginationDto;
import com.wealth.bss.common.model.entity.WealthAccountBalanceEntity;
import com.wealth.bss.constants.WealthServiceConstants;
import com.wealth.bss.query.QueryConstants;

class WealthAccountBalanceDaoImpleTest {

	private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
	private final WealthAccountBalanceDaoImple dao = new WealthAccountBalanceDaoImple(jdbc);

	private WealthAccountBalanceEntity balance() {
		WealthAccountBalanceEntity b = new WealthAccountBalanceEntity();
		b.setUserId("user-1");
		b.setAccountId("acc-1");
		b.setAsOf(new Timestamp(1000));
		b.setBalance(new BigDecimal("2500.50"));
		b.setStatus("reported");
		b.setEnteredBy(WealthServiceConstants.ACTOR_AGENT);
		return b;
	}

	@SuppressWarnings("unchecked")
	@Test
	void historyDefaultsToThirtyRows() {
		when(jdbc.query(eq(QueryConstants.FIND_BALANCE_HISTORY), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
		PaginationDto dto = new PaginationDto();
		dto.setUserId("user-1");
		dto.setId("acc-1");

		dao.findHistory(dto);

		ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
		verify(jdbc).query(eq(QueryConstants.FIND_BALANCE_HISTORY), any(RowMapper.class), args.capture());
		assertEquals("user-1", args.getValue()[0]);
		assertEquals("acc-1", args.getValue()[1]);
		assertEquals(WealthServiceConstants.BALANCE_HISTORY_DEFAULT, args.getValue()[2]);
	}

	@SuppressWarnings("unchecked")
	@Test
	void insertSetsTheActorAndReturnsNullWhenAlreadyStored() {
		when(jdbc.query(eq(QueryConstants.INSERT_ACCOUNT_BALANCE), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
		WealthAccountBalanceEntity input = balance();
		input.setActor(WealthServiceConstants.ACTOR_AGENT);

		assertNull(dao.insert(input));

		verify(jdbc).queryForObject(QueryConstants.SET_ACTOR, String.class, WealthServiceConstants.ACTOR_AGENT, "");
	}

	@SuppressWarnings("unchecked")
	@Test
	void insertPassesTheCallerSoAForeignAccountIsRefusedBySql() {
		WealthAccountBalanceEntity stored = balance();
		when(jdbc.query(eq(QueryConstants.INSERT_ACCOUNT_BALANCE), any(RowMapper.class), any(Object[].class))).thenReturn(List.of(stored));

		assertEquals("acc-1", dao.insert(balance()).getAccountId());

		ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
		verify(jdbc).query(eq(QueryConstants.INSERT_ACCOUNT_BALANCE), any(RowMapper.class), args.capture());
		assertEquals(7, args.getValue().length);
		assertEquals("user-1", args.getValue()[5]);
		assertEquals("acc-1", args.getValue()[6]);
		assertTrue(QueryConstants.INSERT_ACCOUNT_BALANCE.contains("A.USER_ID = CAST(? AS UUID)"));
	}

	@Test
	void accountCopyMovesOnlyForwardInTime() {
		when(jdbc.queryForList(eq(QueryConstants.UPDATE_ACCOUNT_BALANCE_COPY), eq(String.class), any(Object[].class))).thenReturn(List.of("acc-1"));
		assertTrue(dao.updateAccountCopy(balance()));
		assertTrue(QueryConstants.UPDATE_ACCOUNT_BALANCE_COPY.contains("BALANCE_AS_OF IS NULL OR BALANCE_AS_OF < ?"));

		when(jdbc.queryForList(eq(QueryConstants.UPDATE_ACCOUNT_BALANCE_COPY), eq(String.class), any(Object[].class))).thenReturn(List.of());
		assertFalse(dao.updateAccountCopy(balance()));
	}
}
