package com.wealth.bss.nexus.serviceimple;

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

class RequestHeaderServiceImpleTest {

	private IfNexusCacheService nexusCache;
	private RequestHeaderServiceImple service;

	@BeforeEach
	void setUp() {
		nexusCache = mock(IfNexusCacheService.class);
		service = new RequestHeaderServiceImple(nexusCache, "wealth:nexus", 300);
	}

	@Test
	void theKeyIsNamespacedAndTheValueIsStoredWithTheTtlThroughTheNexusCache() {
		when(nexusCache.get("wealth:nexus-k")).thenReturn("v");

		assertEquals("v", service.getCacheData("k"));
		assertEquals("wealth:nexus-k", service.setCacheData("k", "v"));

		verify(nexusCache).put("wealth:nexus-k", "v", Duration.ofSeconds(300));
	}

	@Test
	void aRemovalGoesThroughTheNexusCacheWhichClearsBothStores() {
		when(nexusCache.remove("wealth:nexus-k")).thenReturn(true);

		assertTrue(service.removeCacheData("k"));
		when(nexusCache.remove("wealth:nexus-k")).thenReturn(false);
		assertFalse(service.removeCacheData("k"));
	}
}
