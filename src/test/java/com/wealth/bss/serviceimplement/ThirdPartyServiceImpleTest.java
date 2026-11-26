package com.wealth.bss.serviceimplement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.wealth.bss.common.model.dao.IfWealthConnectionDao;
import com.wealth.bss.common.model.entity.WealthConnectionEntity;
import com.wealth.bss.config.AngelOneClient;
import com.wealth.bss.config.ConnectionTester;
import com.wealth.bss.config.SettingsCipher;
import com.wealth.bss.serviceinterface.IfRequestContextService;
import com.wealth.common.generic.BaseError;
import com.wealth.common.generic.KarthiException;
import com.wealth.common.model.dto.admin.ProviderRequestDto;
import com.wealth.common.model.dto.admin.ThirdPartyDto;
import com.wealth.common.model.dto.admin.ThirdPartySaveRequestDto;

class ThirdPartyServiceImpleTest {

	private static final String KEY = Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());

	private IfRequestContextService context;
	private IfWealthConnectionDao dao;
	private SettingsCipher cipher;
	private ConnectionTester tester;
	private AngelOneClient angel;
	private ThirdPartyServiceImple service;

	@BeforeEach
	void setUp() {
		context = mock(IfRequestContextService.class);
		dao = mock(IfWealthConnectionDao.class);
		cipher = new SettingsCipher(KEY);
		tester = mock(ConnectionTester.class);
		angel = org.mockito.Mockito.spy(new AngelOneClient());
		service = new ThirdPartyServiceImple(context, dao, cipher, tester, angel);
		when(dao.upsert(any())).thenAnswer(i -> i.getArgument(0));
	}

	private ThirdPartySaveRequestDto save(String provider, String label, String url, String apiKey, boolean enabled, int priority) {
		ThirdPartySaveRequestDto r = new ThirdPartySaveRequestDto();
		r.setProvider(provider);
		r.setLabel(label);
		r.setBaseUrl(url);
		r.setApiKey(apiKey);
		r.setEnabled(enabled);
		r.setPriority(priority);
		return r;
	}

	private ProviderRequestDto provider(String key) {
		ProviderRequestDto r = new ProviderRequestDto();
		r.setProvider(key);
		return r;
	}

	private WealthConnectionEntity row(String key, String encrypted) {
		WealthConnectionEntity e = new WealthConnectionEntity();
		e.setProvider(key);
		e.setLabel(key);
		e.setBaseUrl("https://api.example.com");
		e.setCredentialEnc(encrypted);
		e.setPriority(1);
		return e;
	}

	private String codeOf(Runnable call) {
		return assertThrows(KarthiException.class, call::run).getMessage();
	}

	@Test
	void everyActionIsForAnAdminOnly() {
		doThrow(new KarthiException(BaseError.ADMIN_ONLY_ACTION)).when(context).requireAdmin();
		assertEquals(BaseError.ADMIN_ONLY_ACTION, codeOf(service::list));
		assertEquals(BaseError.ADMIN_ONLY_ACTION, codeOf(() -> service.save(save("gemini", "G", "https://a.example.com", "k", true, 1))));
		assertEquals(BaseError.ADMIN_ONLY_ACTION, codeOf(() -> service.test(provider("gemini"))));
		assertEquals(BaseError.ADMIN_ONLY_ACTION, codeOf(() -> service.remove(provider("gemini"))));
		verify(dao, never()).upsert(any());
		verify(dao, never()).delete(any());
	}

	@Test
	void theListShowsWhetherAKeyIsSetAndItsLastCharactersNeverTheKey() {
		WealthConnectionEntity gemini = row("gemini", cipher.encrypt("AIza-secret-1234"));
		gemini.setKeyHint("1234");
		gemini.setLastStatus("ok");
		when(dao.findAll()).thenReturn(List.of(gemini, row("yahoo", null)));

		List<ThirdPartyDto> list = service.list();

		assertTrue(list.get(0).isKeySet());
		assertEquals("1234", list.get(0).getKeyHint());
		assertEquals("ok", list.get(0).getStatus());
		assertFalse(list.get(1).isKeySet());
		assertNull(list.get(1).getKeyHint());
	}

	@Test
	void aKeyIsEncryptedWithItsHintAndAnEmptyKeyKeepsTheSavedOne() {
		service.save(save("gemini", " Gemini ", "https://generativelanguage.googleapis.com/", "AIza-secret-1234", true, 2));

		ArgumentCaptor<WealthConnectionEntity> saved = ArgumentCaptor.forClass(WealthConnectionEntity.class);
		verify(dao).upsert(saved.capture());
		assertEquals("Gemini", saved.getValue().getLabel());
		assertEquals("1234", saved.getValue().getKeyHint());
		assertEquals("AIza-secret-1234", cipher.decrypt(saved.getValue().getCredentialEnc()));
		assertTrue(saved.getValue().isEnabled());

		service.save(save("gemini", "Gemini", "https://generativelanguage.googleapis.com", "", false, 2));
		ArgumentCaptor<WealthConnectionEntity> again = ArgumentCaptor.forClass(WealthConnectionEntity.class);
		verify(dao, org.mockito.Mockito.times(2)).upsert(again.capture());
		assertNull(again.getValue().getCredentialEnc(), "null means keep the saved key");
		assertNull(again.getValue().getKeyHint());
	}

	@Test
	void withoutTheServerKeyAKeyCannotBeSavedButTheRestCan() {
		ThirdPartyServiceImple noKey = new ThirdPartyServiceImple(context, dao, new SettingsCipher(""), tester, angel);
		assertEquals(BaseError.SETTINGS_KEY_MISSING, codeOf(() -> noKey.save(save("gemini", "G", "https://a.example.com", "k", true, 1))));
		verify(dao, never()).upsert(any());
		noKey.save(save("gemini", "G", "https://a.example.com", "", true, 1));
		verify(dao).upsert(any());
	}

	@Test
	void badProviderDetailsAreRefusedBeforeAnythingIsSaved() {
		String[][] bad = { { "Bad Name", "L", "https://a.example.com" }, { "x", "L", "https://a.example.com" }, { "ok-name", "", "https://a.example.com" },
				{ "ok-name", "x".repeat(61), "https://a.example.com" }, { "ok-name", "L", "ftp://a.example.com" }, { "ok-name", "L", "a.example.com" },
				{ "ok-name", "L", "https://169.254.169.254" }, { "ok-name", "L", "https://a.example.com/a b" }, { "ok-name", "L", "" } };
		for (String[] b : bad) {
			assertEquals(BaseError.PROVIDER_INVALID, codeOf(() -> service.save(save(b[0], b[1], b[2], "", true, 1))), String.join("|", b));
		}
		assertEquals(BaseError.PROVIDER_INVALID, codeOf(() -> service.save(save("ok-name", "L", "https://a.example.com", "", true, 0))));
		assertEquals(BaseError.PROVIDER_INVALID, codeOf(() -> service.save(save("ok-name", "L", "https://a.example.com", "", true, 100))));
		assertEquals(BaseError.PROVIDER_INVALID, codeOf(() -> service.save(null)));
		assertEquals(BaseError.ENVIRONMENT_CREDENTIALS_INVALID, codeOf(() -> service.save(save("ok-name", "L", "https://a.example.com", "a" + (char) 7, true, 1))));
		assertEquals(BaseError.ENVIRONMENT_CREDENTIALS_INVALID, codeOf(() -> service.save(save("ok-name", "L", "https://a.example.com", "k".repeat(501), true, 1))));
		verify(dao, never()).upsert(any());
		service.save(save("ok-name", "L", "https://a.example.com/v1/api", "", true, 1));
		verify(dao).upsert(any());
	}

	@Test
	void aTestUsesTheSavedKeyAndSavesWhatHappened() {
		when(dao.findAll()).thenReturn(List.of(row("gemini", cipher.encrypt("the-key"))));
		when(tester.test("gemini", "https://api.example.com", "the-key")).thenReturn(true);
		WealthConnectionEntity after = row("gemini", "x");
		after.setLastStatus("ok");
		when(dao.saveTestResult(any())).thenReturn(after);

		ThirdPartyDto result = service.test(provider("gemini"));

		assertEquals("ok", result.getStatus());
		ArgumentCaptor<WealthConnectionEntity> saved = ArgumentCaptor.forClass(WealthConnectionEntity.class);
		verify(dao).saveTestResult(saved.capture());
		assertEquals("ok", saved.getValue().getLastStatus());

		when(tester.test(eq("gemini"), any(), any())).thenReturn(false);
		service.test(provider("gemini"));
		verify(dao, org.mockito.Mockito.times(2)).saveTestResult(saved.capture());
		assertEquals("failed", saved.getValue().getLastStatus());
	}

	private ThirdPartySaveRequestDto angelSave(java.util.Map<String, String> extra, boolean autoFetch) {
		ThirdPartySaveRequestDto r = save("angel-one", "Angel One", "https://apiconnect.angelone.in", "", true, 3);
		r.setExtra(extra);
		r.setAutoFetch(autoFetch);
		return r;
	}

	@Test
	void angelOneLoginValuesAreKeptEncryptedMergedWithWhatIsSavedAndOnlyTheirNamesAreShown() {
		try (var header = org.mockito.Mockito.mockStatic(com.wealth.common.utils.generic.RequestHeaderUtil.class)) {
			header.when(com.wealth.common.utils.generic.RequestHeaderUtil::getUserId).thenReturn("11111111-1111-1111-1111-111111111111");
			WealthConnectionEntity saved = row("angel-one", cipher.encrypt("k"));
			saved.setCredentialExtraEnc(cipher.encrypt("{\"clientCode\":\"A123\",\"pin\":\"1234\"}"));
			when(dao.findAll()).thenReturn(List.of(saved));

			ThirdPartyDto dto = service.save(angelSave(java.util.Map.of("totpSecret", " JBSWY3DP ", "pin", ""), true));

			ArgumentCaptor<WealthConnectionEntity> entity = ArgumentCaptor.forClass(WealthConnectionEntity.class);
			verify(dao).upsert(entity.capture());
			assertEquals("11111111-1111-1111-1111-111111111111", entity.getValue().getOwnerUserId());
			assertTrue(entity.getValue().isAutoFetch());
			java.util.Map<String, String> stored = angel.readExtra(cipher.decrypt(entity.getValue().getCredentialExtraEnc()));
			assertEquals("A123", stored.get("clientCode"), "a value not sent is kept");
			assertEquals("1234", stored.get("pin"), "an empty value keeps the saved one");
			assertEquals("JBSWY3DP", stored.get("totpSecret"));
			assertFalse(entity.getValue().getCredentialExtraEnc().contains("JBSWY3DP"));
			assertFalse(dto.getExtraSet().isEmpty());
		}
	}

	@Test
	void nothingEnteredKeepsTheSavedLoginValuesAndOtherProvidersRefuseThem() {
		service.save(angelSave(java.util.Map.of("pin", ""), false));
		ArgumentCaptor<WealthConnectionEntity> entity = ArgumentCaptor.forClass(WealthConnectionEntity.class);
		verify(dao).upsert(entity.capture());
		assertNull(entity.getValue().getCredentialExtraEnc());

		ThirdPartySaveRequestDto other = save("gemini", "G", "https://a.example.com", "", true, 1);
		other.setExtra(java.util.Map.of("pin", "1"));
		assertEquals(BaseError.PROVIDER_INVALID, codeOf(() -> service.save(other)));
		other.setExtra(null);
		other.setAutoFetch(true);
		assertEquals(BaseError.PROVIDER_INVALID, codeOf(() -> service.save(other)));
		assertEquals(BaseError.ENVIRONMENT_CREDENTIALS_INVALID, codeOf(() -> service.save(angelSave(java.util.Map.of("unknown", "x"), false))));
		assertEquals(BaseError.ENVIRONMENT_CREDENTIALS_INVALID, codeOf(() -> service.save(angelSave(java.util.Map.of("pin", "x".repeat(101)), false))));
		assertEquals(BaseError.ENVIRONMENT_CREDENTIALS_INVALID, codeOf(() -> service.save(angelSave(java.util.Map.of("pin", "a" + (char) 7 + "b"), false))));
	}

	@Test
	void testingAngelOneDoesARealSignInWhenTheLoginIsCompleteAndOnlyReachabilityOtherwise() {
		WealthConnectionEntity full = row("angel-one", cipher.encrypt("api-key"));
		full.setCredentialExtraEnc(cipher.encrypt("{\"clientCode\":\"A\",\"pin\":\"1\",\"totpSecret\":\"JBSWY3DP\",\"macAddress\":\"00:11\"}"));
		when(dao.findAll()).thenReturn(List.of(full));
		org.mockito.Mockito.doReturn("jwt").when(angel).login(any(), any());
		service.test(provider("angel-one"));
		verify(angel).login(eq("https://api.example.com"), any());
		ArgumentCaptor<WealthConnectionEntity> saved = ArgumentCaptor.forClass(WealthConnectionEntity.class);
		verify(dao).saveTestResult(saved.capture());
		assertEquals("ok", saved.getValue().getLastStatus());

		org.mockito.Mockito.doThrow(new IllegalStateException("Invalid totp")).when(angel).login(any(), any());
		service.test(provider("angel-one"));
		verify(dao, org.mockito.Mockito.times(2)).saveTestResult(saved.capture());
		assertEquals("failed", saved.getValue().getLastStatus());
		verify(tester, never()).test(any(), any(), any());

		when(dao.findAll()).thenReturn(List.of(row("angel-one", cipher.encrypt("api-key"))));
		service.test(provider("angel-one"));
		verify(tester).test(eq("angel-one"), any(), eq("api-key"));
	}

	@Test
	void testingOrRemovingAProviderThatIsNotThereIsRefused() {
		when(dao.findAll()).thenReturn(List.of(row("gemini", null)));
		assertEquals(BaseError.PROVIDER_NOT_FOUND, codeOf(() -> service.test(provider("ghost"))));
		assertEquals(BaseError.PROVIDER_NOT_FOUND, codeOf(() -> service.remove(provider("ghost"))));
		assertEquals(BaseError.PROVIDER_NOT_FOUND, codeOf(() -> service.remove(null)));
		verify(dao, never()).delete(any());
		service.remove(provider("gemini"));
		verify(dao).delete("gemini");
	}
}
