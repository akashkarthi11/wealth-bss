package com.wealth.bss.serviceimplement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wealth.bss.common.model.dao.IfWealthUserDao;
import com.wealth.bss.common.model.dto.PaginationDto;
import com.wealth.bss.common.model.entity.WealthUserEntity;
import com.wealth.bss.nexus.service.IfRequestHeaderService;
import com.wealth.common.model.dto.ThreadLocalDto;

class RequestContextServiceImpleTest {

	private static final String ID = "3f2b8c1e-0a4d-4c55-9d0e-1b6c7a8d9e10";
	private static final String KEY = "requestContext:" + ID;

	private IfRequestHeaderService cache;
	private IfWealthUserDao dao;
	private RequestContextServiceImple service;

	@BeforeEach
	void setUp() {
		cache = mock(IfRequestHeaderService.class);
		dao = mock(IfWealthUserDao.class);
		service = new RequestContextServiceImple(cache, dao);
	}

	private WealthUserEntity user() {
		WealthUserEntity u = new WealthUserEntity();
		u.setId(ID);
		u.setEmail("someone@example.com");
		u.setRole("admin");
		u.setSessionVersion(3);
		return u;
	}

	@Test
	void cacheHitSkipsTheDatabase() {
		when(cache.getCacheData(KEY)).thenReturn("{\"userId\":\"" + ID + "\",\"role\":\"user\",\"sessionVersion\":2}");
		ThreadLocalDto dto = service.getRequestHeaderCache(ID);
		assertEquals("user", dto.getRole());
		assertEquals(2, dto.getSessionVersion());
		verify(dao, never()).findById(any());
	}

	@Test
	void cacheMissLoadsTheUserAndCachesOnlyIdentityFields() {
		when(cache.getCacheData(KEY)).thenReturn(null);
		when(dao.findById(argThat(p -> ID.equals(p.getId())))).thenReturn(user());
		ThreadLocalDto dto = service.getRequestHeaderCache(ID);
		assertEquals("admin", dto.getRole());
		assertEquals(3, dto.getSessionVersion());
		verify(cache).setCacheData(eq(KEY), argThat(json -> json.contains("\"role\":\"admin\"")
				&& !json.contains("email") && !json.contains("someone@example.com")));
	}

	@Test
	void unknownUserReturnsNullAndCachesNothing() {
		when(cache.getCacheData(KEY)).thenReturn(null);
		when(dao.findById(any(PaginationDto.class))).thenReturn(null);
		assertNull(service.getRequestHeaderCache(ID));
		verify(cache, never()).setCacheData(anyString(), anyString());
	}

	@Test
	void subjectThatIsNotAUuidNeverReachesRedisOrTheDatabase() {
		assertNull(service.getRequestHeaderCache("not-a-uuid"));
		assertNull(service.getRequestHeaderCache("a:b*"));
		assertNull(service.getRequestHeaderCache(null));
		verify(cache, never()).getCacheData(anyString());
		verify(dao, never()).findById(any());
	}

	@Test
	void redisFailureFallsBackToTheDatabase() {
		when(cache.getCacheData(KEY)).thenThrow(new IllegalStateException("redis down"));
		when(cache.setCacheData(anyString(), anyString())).thenThrow(new IllegalStateException("redis down"));
		when(dao.findById(any(PaginationDto.class))).thenReturn(user());
		assertEquals("admin", service.getRequestHeaderCache(ID).getRole());
	}

	@Test
	void corruptCacheEntryFallsBackToTheDatabase() {
		when(cache.getCacheData(KEY)).thenReturn("{not json");
		when(dao.findById(any(PaginationDto.class))).thenReturn(user());
		assertEquals("admin", service.getRequestHeaderCache(ID).getRole());
	}

	@Test
	void evictRemovesTheKeyAndIgnoresBadIdsAndRedisErrors() {
		service.evict(ID);
		verify(cache).removeCacheData(KEY);
		service.evict("garbage");
		when(cache.removeCacheData(KEY)).thenThrow(new IllegalStateException("redis down"));
		service.evict(ID);
	}
}
