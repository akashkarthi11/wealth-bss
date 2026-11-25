package com.wealth.bss.daoimplement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import com.wealth.bss.common.model.dto.PaginationDto;
import com.wealth.bss.common.model.entity.WealthUserEntity;
import com.wealth.bss.query.QueryConstants;

class WealthUserDaoImpleTest {

	@SuppressWarnings("unchecked")
	@Test
	void findByIdPassesTheIdAndReturnsTheEntity() {
		JdbcTemplate jdbc = mock(JdbcTemplate.class);
		WealthUserEntity row = new WealthUserEntity();
		row.setRole("user");
		when(jdbc.queryForObject(eq(QueryConstants.FIND_WEALTH_USER_BY_ID), any(RowMapper.class), eq("abc"))).thenReturn(row);
		PaginationDto dto = new PaginationDto();
		dto.setId("abc");
		assertEquals("user", new WealthUserDaoImple(jdbc).findById(dto).getRole());
	}

	@SuppressWarnings("unchecked")
	@Test
	void noRowReturnsNull() {
		JdbcTemplate jdbc = mock(JdbcTemplate.class);
		when(jdbc.queryForObject(eq(QueryConstants.FIND_WEALTH_USER_BY_ID), any(RowMapper.class), eq("abc")))
				.thenThrow(new EmptyResultDataAccessException(1));
		PaginationDto dto = new PaginationDto();
		dto.setId("abc");
		assertNull(new WealthUserDaoImple(jdbc).findById(dto));
	}

	@Test
	void queryNeverSelectsThePasswordHash() {
		assertEquals(false, QueryConstants.FIND_WEALTH_USER_BY_ID.toUpperCase().contains("PASSWORD"));
	}
}
