package com.wealth.bss.serviceimplement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wealth.bss.common.model.dao.IfWealthConnectionDao;
import com.wealth.bss.common.model.dao.IfWealthUserConnectionDao;
import com.wealth.bss.common.model.entity.WealthConnectionEntity;
import com.wealth.bss.common.model.entity.WealthUserConnectionEntity;
import com.wealth.bss.config.IndstocksClient;
import com.wealth.bss.config.SettingsCipher;
import com.wealth.common.generic.BaseError;
import com.wealth.common.generic.KarthiException;
import com.wealth.common.model.dto.admin.IndstocksSaveRequestDto;
import com.wealth.common.model.dto.admin.IndstocksStatusDto;
import com.wealth.common.utils.generic.RequestHeaderUtil;

class IndstocksServiceImpleTest {

	private static final String KEY = Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());
	private static final String USER = "11111111-1111-1111-1111-111111111111";
	private static final String OTHER = "22222222-2222-2222-2222-222222222222";

	private IfWealthConnectionDao connections;
	private IfWealthUserConnectionDao users;
	private SettingsCipher cipher;
	private IndstocksClient client;
	private IndstocksServiceImple service;
	private final Map<String, WealthUserConnectionEntity> store = new HashMap<>();
	private WealthConnectionEntity provider;

	@BeforeEach
	void setUp() {
		connections = mock(IfWealthConnectionDao.class);
		users = mock(IfWealthUserConnectionDao.class);
		client = mock(IndstocksClient.class);
		cipher = new SettingsCipher(KEY);
		provider = new WealthConnectionEntity();
		provider.setProvider("indstocks");
		provider.setBaseUrl("https://api.indstocks.com");
		provider.setEnabled(true);
		when(connections.findAll()).thenAnswer(i -> List.of(provider));
		store.clear();
		when(users.find(any(), any())).thenAnswer(i -> store.get(i.getArgument(0) + "|" + i.getArgument(1)));
		when(users.upsert(any())).thenAnswer(i -> {
			WealthUserConnectionEntity e = i.getArgument(0);
			store.put(e.getUserId() + "|" + e.getProvider(), e);
			return e;
		});
		when(users.saveCheck(any(), any(), any())).thenAnswer(i -> {
			WealthUserConnectionEntity e = store.get(i.getArgument(0) + "|" + i.getArgument(1));
			if (e != null) {
				e.setLastStatus(i.getArgument(2));
				e.setLastCheckedAt(new Timestamp(1_000L));
			}
			return e;
		});
		when(users.delete(any(), any())).thenAnswer(i -> store.remove(i.getArgument(0) + "|" + i.getArgument(1)) != null);
		RequestHeaderUtil.setUserId(USER);
		service = new IndstocksServiceImple(connections, users, cipher, client);
	}

	private IndstocksSaveRequestDto token(String value) {
		IndstocksSaveRequestDto request = new IndstocksSaveRequestDto();
		request.setToken(value);
		return request;
	}

	private String codeOf(Runnable call) {
		return assertThrows(KarthiException.class, call::run).getMessage();
	}

	@Test
	void theTokenIsSavedEncryptedPerUserAndNeverReturned() {
		IndstocksStatusDto before = service.status();
		assertTrue(before.isProviderOn());
		assertFalse(before.isTokenSet());

		IndstocksStatusDto saved = service.save(token("  Bearer abc.def-123456  "));
		assertTrue(saved.isTokenSet());
		WealthUserConnectionEntity row = store.get(USER + "|indstocks");
		assertEquals("abc.def-123456", cipher.decrypt(row.getCredentialEnc()), "trimmed and without a pasted Bearer prefix");
		assertFalse(row.getCredentialEnc().contains("abc.def"), "stored encrypted");
		assertEquals("3456", row.getKeyHint());

		RequestHeaderUtil.setUserId(OTHER);
		assertFalse(service.status().isTokenSet(), "another user sees nothing of it");
	}

	@Test
	void aBadTokenOrAMissingProviderRowIsRefusedAndNothingIsSaved() {
		for (String bad : new String[] { null, "", "   ", "two words", "has\ttab", "x".repeat(501) }) {
			assertEquals(BaseError.PROVIDER_INVALID, codeOf(() -> service.save(token(bad))));
		}
		assertEquals(BaseError.PROVIDER_INVALID, codeOf(() -> service.save(null)));
		assertTrue(store.isEmpty());
		when(connections.findAll()).thenReturn(List.of());
		assertEquals(BaseError.PROVIDER_NOT_FOUND, codeOf(() -> service.save(token("abc"))));
	}

	@Test
	void aTestMarksTheTokenWorkingOrRefusedAndAnUncertainAnswerChangesNothing() {
		assertEquals(BaseError.CREDENTIALS_INCOMPLETE, codeOf(() -> service.test()));
		service.save(token("tok-1234"));

		when(client.probe("https://api.indstocks.com", "tok-1234")).thenReturn(200);
		assertEquals("ok", service.test().getStatus());
		when(client.probe(any(), any())).thenReturn(401);
		assertEquals("failed", service.test().getStatus());
		when(client.probe(any(), any())).thenReturn(200);
		assertEquals("ok", service.test().getStatus());

		when(client.probe(any(), any())).thenReturn(429);
		assertEquals(BaseError.BROKER_FETCH_FAILED, codeOf(() -> service.test()));
		when(client.probe(any(), any())).thenThrow(new IllegalStateException("INDstocks could not be reached"));
		assertEquals(BaseError.BROKER_FETCH_FAILED, codeOf(() -> service.test()));
		assertEquals("ok", service.status().getStatus(), "a 429 or a network failure says nothing about the token");
	}

	@Test
	void aTestNeedsTheProviderSwitchedOnAndNoCallIsMadeWithoutIt() {
		service.save(token("tok-1234"));
		provider.setEnabled(false);
		assertEquals(BaseError.PROVIDER_NOT_FOUND, codeOf(() -> service.test()));
		verify(client, never()).probe(any(), any());
		assertFalse(service.status().isProviderOn());
	}

	@Test
	void removingTheTokenDeletesOnlyTheCallersOwn() {
		service.save(token("tok-1234"));
		RequestHeaderUtil.setUserId(OTHER);
		assertFalse(service.remove(), "the other user has none");
		RequestHeaderUtil.setUserId(USER);
		assertTrue(service.remove());
		assertNull(store.get(USER + "|indstocks"));
		assertFalse(service.status().isTokenSet());
	}
}
