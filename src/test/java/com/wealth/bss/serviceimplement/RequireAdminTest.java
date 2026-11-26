package com.wealth.bss.serviceimplement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import com.wealth.bss.common.model.dao.IfWealthUserDao;
import com.wealth.bss.nexus.service.IfRequestHeaderService;
import com.wealth.common.client.NexusServiceClient;
import com.wealth.common.generic.BaseError;
import com.wealth.common.generic.KarthiException;
import com.wealth.common.model.dto.ThreadLocalDto;
import com.wealth.common.utils.generic.RequestHeaderUtil;

class RequireAdminTest {

	private static final String ID = "3f2b8c1e-0a4d-4c55-9d0e-1b6c7a8d9e10";

	private NexusServiceClient nexus;
	private final RequestContextServiceImple service = new RequestContextServiceImple(mock(IfRequestHeaderService.class), mock(IfWealthUserDao.class));

	@SuppressWarnings("unchecked")
	@BeforeEach
	void setUp() {
		nexus = mock(NexusServiceClient.class);
		ObjectProvider<NexusServiceClient> provider = mock(ObjectProvider.class);
		when(provider.getIfAvailable()).thenReturn(nexus);
		new RequestHeaderUtil(provider);
		RequestHeaderUtil.setUserId(ID);
	}

	@AfterEach
	void tearDown() {
		RequestHeaderUtil.clear();
	}

	private void callerHasRole(String role) {
		ThreadLocalDto dto = new ThreadLocalDto();
		dto.setUserId(ID);
		dto.setRole(role);
		when(nexus.getRequestHeaderCache(ID)).thenReturn(dto);
	}

	@Test
	void anAdminPasses() {
		callerHasRole("admin");
		service.requireAdmin();
	}

	@Test
	void aPlainUserIsRefused() {
		callerHasRole("user");
		assertEquals(BaseError.ADMIN_ONLY_ACTION, assertThrows(KarthiException.class, service::requireAdmin).getMessage());
	}

	@Test
	void noCallerIsRefused() {
		RequestHeaderUtil.clear();
		assertEquals(BaseError.ADMIN_ONLY_ACTION, assertThrows(KarthiException.class, service::requireAdmin).getMessage());
	}
}
