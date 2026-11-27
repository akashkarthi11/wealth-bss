package com.wealth.bss.common.utils.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wealth.bss.nexus.service.IfNexusCacheService;

class LoginRateLimiterTest {

	private IfNexusCacheService nexusCache;
	private LoginRateLimiter limiter;

	@BeforeEach
	void setUp() {
		nexusCache = mock(IfNexusCacheService.class);
		limiter = new LoginRateLimiter(nexusCache);
	}

	@Test
	void theCountsAreReadCountedAndResetUnderTheSameKeysAsTheNodeApp() {
		when(nexusCache.count("wealth:rl:login:ip:1.2.3.4")).thenReturn(3L);
		when(nexusCache.increment("wealth:rl:login:ip:1.2.3.4", Duration.ofSeconds(900))).thenReturn(4L);

		assertEquals(3, limiter.count("login:ip:1.2.3.4"));
		assertTrue(limiter.attempt("login:ip:1.2.3.4", 5, 900));
		assertFalse(limiter.attempt("login:ip:1.2.3.4", 3, 900));
		limiter.reset("login:ip:1.2.3.4");

		verify(nexusCache).remove("wealth:rl:login:ip:1.2.3.4");
	}

	@Test
	void anAttemptIsAllowedUpToTheLimitAndNotBeyond() {
		when(nexusCache.increment("wealth:rl:k", Duration.ofSeconds(60))).thenReturn(1L, 2L, 3L);

		assertTrue(limiter.attempt("k", 2, 60));
		assertTrue(limiter.attempt("k", 2, 60));
		assertFalse(limiter.attempt("k", 2, 60), "the third attempt is over the limit");
	}
}
