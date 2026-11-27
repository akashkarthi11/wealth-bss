package com.wealth.bss.serviceimplement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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

import com.wealth.bss.common.model.dao.IfWealthCommonConnectionDao;
import com.wealth.bss.common.model.dao.IfWealthConnectionDao;
import com.wealth.bss.common.model.dao.IfWealthTransactionDao;
import com.wealth.bss.common.model.dao.IfWealthUserConnectionDao;
import com.wealth.bss.common.model.entity.WealthTransactionEntity;
import com.wealth.bss.nexus.service.IfNexusReadService;
import com.wealth.common.model.dto.admin.GmailFetchResultDto;
import com.wealth.common.model.dto.admin.GmailReviewRequestDto;
import com.wealth.common.model.dto.admin.GmailStatementSettingsRequestDto;
import com.wealth.bss.common.model.entity.WealthCommonConnectionEntity;
import com.wealth.bss.common.model.entity.WealthConnectionEntity;
import com.wealth.bss.common.model.entity.WealthUserConnectionEntity;
import com.wealth.bss.config.GmailClient;
import com.wealth.bss.config.SettingsCipher;
import com.wealth.bss.serviceinterface.IfRequestContextService;
import com.wealth.common.generic.BaseError;
import com.wealth.common.generic.KarthiException;
import com.wealth.common.model.dto.admin.GmailAppSaveRequestDto;
import com.wealth.common.model.dto.admin.GmailConnectRequestDto;
import com.wealth.common.model.dto.admin.GmailFinishRequestDto;
import com.wealth.common.model.dto.admin.GmailStatusDto;
import com.wealth.common.utils.generic.RequestHeaderUtil;

class GmailServiceImpleTest {

	private static final String KEY = Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());
	private static final String REDIRECT = "https://wealth.example.com/my-connections/gmail/callback";
	private static final String API = "https://gmail.googleapis.com";

	private IfRequestContextService context;
	private IfWealthConnectionDao main;
	private SettingsCipher cipher;
	private GmailClient client;
	private com.wealth.bss.config.GeminiClient gemini;
	private WealthConnectionEntity mainRow;
	private WealthCommonConnectionEntity commonRow;
	private WealthUserConnectionEntity userRow;
	private IfWealthTransactionDao transactions;
	private com.wealth.bss.common.model.dao.IfWealthTransactionWorkflowDao workflow;
	private IfNexusReadService nexus;
	private com.wealth.bss.common.model.dao.IfWealthStatementFigureDao statements;
	private GmailServiceImple service;

	@BeforeEach
	void setUp() {
		RequestHeaderUtil.setUserId("user-1");
		context = mock(IfRequestContextService.class);
		main = mock(IfWealthConnectionDao.class);
		cipher = new SettingsCipher(KEY);
		client = mock(GmailClient.class);
		gemini = mock(com.wealth.bss.config.GeminiClient.class);
		mainRow = new WealthConnectionEntity();
		mainRow.setProvider("gmail");
		mainRow.setBaseUrl(API);
		mainRow.setEnabled(false);
		when(main.findAll()).thenAnswer(i -> List.of(mainRow));
		when(main.upsert(any())).thenAnswer(i -> {
			WealthConnectionEntity saved = i.getArgument(0);
			mainRow = saved;
			return saved;
		});
		commonRow = null;
		userRow = null;
		IfWealthCommonConnectionDao common = new IfWealthCommonConnectionDao() {
			public WealthCommonConnectionEntity find(String provider) {
				return commonRow;
			}

			public WealthCommonConnectionEntity upsert(WealthCommonConnectionEntity entity) {
				commonRow = entity;
				return entity;
			}

			public boolean delete(String provider) {
				boolean had = commonRow != null;
				commonRow = null;
				return had;
			}
		};
		IfWealthUserConnectionDao users = new IfWealthUserConnectionDao() {
			public WealthUserConnectionEntity find(String userId, String provider) {
				return userRow != null && userRow.getUserId().equals(userId) ? userRow : null;
			}

			public java.util.List<WealthUserConnectionEntity> findByProvider(String provider) {
				return userRow == null ? java.util.List.of() : java.util.List.of(userRow);
			}

			public WealthUserConnectionEntity upsert(WealthUserConnectionEntity entity) {
				entity.setLastStatus(null);
				userRow = entity;
				return entity;
			}

			public WealthUserConnectionEntity saveCredential(String userId, String provider, String credentialEnc) {
				if (userRow == null) {
					return null;
				}
				userRow.setCredentialEnc(credentialEnc);
				return userRow;
			}

			public WealthUserConnectionEntity saveCheck(String userId, String provider, String status) {
				if (userRow == null) {
					return null;
				}
				userRow.setLastStatus(status);
				userRow.setLastCheckedAt(new java.sql.Timestamp(System.currentTimeMillis()));
				return userRow;
			}

			public boolean delete(String userId, String provider) {
				boolean had = userRow != null;
				userRow = null;
				return had;
			}
		};
		transactions = mock(IfWealthTransactionDao.class);
		nexus = mock(IfNexusReadService.class);
		workflow = mock(com.wealth.bss.common.model.dao.IfWealthTransactionWorkflowDao.class);
		statements = mock(com.wealth.bss.common.model.dao.IfWealthStatementFigureDao.class);
		service = new GmailServiceImple(context, main, common, users, transactions, nexus, cipher, client,
				new TransactionWorkflowServiceImple(transactions, workflow, mock(com.wealth.bss.common.model.dao.IfWealthAccountDao.class), nexus), statements, gemini);
	}

	private void setUpApp() {
		GmailAppSaveRequestDto app = new GmailAppSaveRequestDto();
		app.setClientId("client-id-1234");
		app.setClientSecret("client-secret-xyz");
		service.appSave(app);
	}

	private GmailConnectRequestDto connect(String redirect) {
		GmailConnectRequestDto request = new GmailConnectRequestDto();
		request.setRedirectUri(redirect);
		return request;
	}

	private GmailFinishRequestDto finish(String code, String state, String redirect) {
		GmailFinishRequestDto request = new GmailFinishRequestDto();
		request.setCode(code);
		request.setState(state);
		request.setRedirectUri(redirect);
		return request;
	}

	private String codeOf(Runnable call) {
		return assertThrows(KarthiException.class, call::run).getMessage();
	}

	// Asks for the consent URL and returns the state the server put in it.
	private String issuedState() {
		when(client.consentUrl(anyString(), anyString(), anyString())).thenReturn("https://accounts.example/consent");
		service.connectUrl(connect(REDIRECT));
		ArgumentCaptor<String> state = ArgumentCaptor.forClass(String.class);
		verify(client, org.mockito.Mockito.atLeastOnce()).consentUrl(eq("client-id-1234"), eq(REDIRECT), state.capture());
		return state.getValue();
	}

	@Test
	void beforeTheGoogleClientIsSavedNobodyCanConnect() {
		GmailStatusDto status = service.status();
		assertFalse(status.isAppConfigured());
		assertFalse(status.isConnected());
		assertEquals(BaseError.GMAIL_NOT_CONFIGURED, codeOf(() -> service.connectUrl(connect(REDIRECT))));
	}

	@Test
	void savingTheGoogleClientIsAdminOnlyEncryptedAndTurnsTheServiceOn() {
		doThrow(new KarthiException(BaseError.ADMIN_ONLY_ACTION)).when(context).requireAdmin();
		GmailAppSaveRequestDto app = new GmailAppSaveRequestDto();
		app.setClientId("client-id-1234");
		app.setClientSecret("client-secret-xyz");
		assertEquals(BaseError.ADMIN_ONLY_ACTION, codeOf(() -> service.appSave(app)));
		assertEquals(BaseError.ADMIN_ONLY_ACTION, codeOf(service::appRemove));
		assertNull(commonRow);

		org.mockito.Mockito.reset(context);
		setUpApp();
		assertTrue(commonRow.getCredentialEnc() != null && !commonRow.getCredentialEnc().contains("client-secret-xyz"));
		assertTrue(cipher.decrypt(commonRow.getCredentialEnc()).contains("client-secret-xyz"));
		assertEquals("1234", commonRow.getKeyHint());
		assertTrue(mainRow.isEnabled());
		assertNull(mainRow.getCredentialEnc(), "the main row holds the config only, never a key");
		assertTrue(service.status().isAppConfigured());
	}

	@Test
	void aBlankSecretKeepsTheSavedOneAndTheFirstSaveNeedsOne() {
		GmailAppSaveRequestDto first = new GmailAppSaveRequestDto();
		first.setClientId("client-id-1234");
		assertEquals(BaseError.GMAIL_REQUEST_INVALID, codeOf(() -> service.appSave(first)));

		setUpApp();
		GmailAppSaveRequestDto idOnly = new GmailAppSaveRequestDto();
		idOnly.setClientId("new-client-id-5678");
		service.appSave(idOnly);
		assertTrue(cipher.decrypt(commonRow.getCredentialEnc()).contains("client-secret-xyz"));
		assertTrue(cipher.decrypt(commonRow.getCredentialEnc()).contains("new-client-id-5678"));
	}

	@Test
	void theSavedClientIdIsShownMaskedToAnAdminOnlyAndTheSecretNeverAndANumberOnlySaveChangesOnlyThat() {
		setUpApp();
		String masked = service.status().getClientIdMasked();
		assertEquals("clie••••••••", masked);
		assertFalse(masked.contains("1234"), "never the whole id");

		GmailAppSaveRequestDto days = new GmailAppSaveRequestDto();
		days.setStatementDays(90);
		service.appSave(days);
		String stored = cipher.decrypt(commonRow.getCredentialEnc());
		assertTrue(stored.contains("client-id-1234") && stored.contains("client-secret-xyz"), "id and secret untouched by a numbers-only save");
		assertEquals(90, service.status().getStatementDays());
		assertEquals(40, service.status().getStatementMax(), "the other numbers keep their value");

		doThrow(new KarthiException(BaseError.ADMIN_ONLY_ACTION)).when(context).requireAdmin();
		assertNull(service.status().getClientIdMasked(), "a user who is not an admin gets no part of the id");
		assertTrue(service.status().isAppConfigured());
	}

	@Test
	void theSearchWindowIsSetInCommonKeptWhenNotSentAndHeldToItsLimits() {
		setUpApp();
		GmailStatusDto fresh = service.status();
		assertEquals(180, fresh.getStatementDays());
		assertEquals(40, fresh.getStatementMax());
		assertEquals(35, fresh.getAlertDays());

		GmailAppSaveRequestDto numbers = new GmailAppSaveRequestDto();
		numbers.setStatementDays(365);
		numbers.setStatementMax(60);
		numbers.setAlertDays(90);
		service.appSave(numbers);
		GmailStatusDto saved = service.status();
		assertEquals(365, saved.getStatementDays());
		assertEquals(60, saved.getStatementMax());
		assertEquals(90, saved.getAlertDays());
		assertTrue(cipher.decrypt(commonRow.getCredentialEnc()).contains("client-secret-xyz"), "only the numbers were sent: the client id and secret stay");

		GmailAppSaveRequestDto idOnly = new GmailAppSaveRequestDto();
		idOnly.setClientId("another-client-id");
		service.appSave(idOnly);
		assertEquals(365, service.status().getStatementDays(), "numbers not sent are kept");

		for (Integer[] bad : new Integer[][] { { 6, 10, 10 }, { 731, 10, 10 }, { 10, 0, 10 }, { 10, 101, 10 }, { 10, 10, 6 }, { 10, 10, 731 } }) {
			GmailAppSaveRequestDto outside = new GmailAppSaveRequestDto();
			outside.setStatementDays(bad[0]);
			outside.setStatementMax(bad[1]);
			outside.setAlertDays(bad[2]);
			assertEquals(BaseError.GMAIL_REQUEST_INVALID, codeOf(() -> service.appSave(outside)));
		}
		assertEquals(365, service.status().getStatementDays(), "a refused save changes nothing");
	}

	@Test
	void removingTheGoogleClientTurnsTheServiceOff() {
		setUpApp();
		assertTrue(service.appRemove());
		assertNull(commonRow);
		assertFalse(mainRow.isEnabled());
		assertFalse(service.status().isAppConfigured());
	}

	@Test
	void theConsentAddressIsOnlyMadeForTheUiCallbackAndCarriesAStateForTheCaller() {
		setUpApp();
		String state = issuedState();
		assertTrue(cipher.decrypt(state).startsWith("user-1|"));

		for (String bad : new String[] { "", "http://wealth.example.com/my-connections/gmail/callback", "https://wealth.example.com/other",
				"https://wealth.example.com/my-connections/gmail/callback?x=1", "https://wealth.example.com/my-connections/gmail/callback#a",
				"https://u:p@wealth.example.com/my-connections/gmail/callback", "javascript:alert(1)", "https://wealth.example.com/my connections" }) {
			assertEquals(BaseError.GMAIL_REQUEST_INVALID, codeOf(() -> service.connectUrl(connect(bad))), bad);
		}
		service.connectUrl(connect("http://localhost:3000/my-connections/gmail/callback"));
	}

	@Test
	void finishingSavesTheEncryptedRefreshTokenAndShowsOnlyAMaskedAddress() {
		setUpApp();
		String state = issuedState();
		when(client.exchange("client-id-1234", "client-secret-xyz", REDIRECT, "the-code")).thenReturn(new GmailClient.Tokens("the-refresh-token", "the-access-token"));
		when(client.mailboxAddress(API, "the-access-token")).thenReturn("karthi@gmail.com");

		GmailStatusDto status = service.connectFinish(finish("the-code", state, REDIRECT));

		assertTrue(status.isConnected());
		assertEquals("k***@gmail.com", status.getEmail());
		assertEquals("ok", status.getStatus());
		assertNotNull(status.getCheckedAt());
		assertEquals("gmail-user", userRow.getProvider());
		assertEquals("user-1", userRow.getUserId());
		assertFalse(userRow.getCredentialEnc().contains("the-refresh-token"));
		assertTrue(cipher.decrypt(userRow.getCredentialEnc()).contains("the-refresh-token"));
	}

	@Test
	void aStateFromAnotherUserATamperedOneOrAnExpiredOneIsRefused() {
		setUpApp();
		String state = issuedState();
		RequestHeaderUtil.setUserId("user-2");
		assertEquals(BaseError.GMAIL_REQUEST_INVALID, codeOf(() -> service.connectFinish(finish("the-code", state, REDIRECT))));
		RequestHeaderUtil.setUserId("user-1");
		assertEquals(BaseError.GMAIL_REQUEST_INVALID, codeOf(() -> service.connectFinish(finish("the-code", "not-a-real-state", REDIRECT))));
		String expired = cipher.encrypt("user-1|" + (System.currentTimeMillis() - 1000));
		assertEquals(BaseError.GMAIL_REQUEST_INVALID, codeOf(() -> service.connectFinish(finish("the-code", expired, REDIRECT))));
		assertEquals(BaseError.GMAIL_REQUEST_INVALID, codeOf(() -> service.connectFinish(finish("", state, REDIRECT))));
		verify(client, never()).exchange(anyString(), anyString(), anyString(), anyString());
		assertNull(userRow);
	}

	@Test
	void whenGoogleRefusesTheCodeNothingIsSaved() {
		setUpApp();
		String state = issuedState();
		when(client.exchange(anyString(), anyString(), anyString(), anyString())).thenThrow(new IllegalStateException("Google refused the request (invalid_grant)"));
		assertEquals(BaseError.GMAIL_CONNECT_FAILED, codeOf(() -> service.connectFinish(finish("the-code", state, REDIRECT))));
		assertNull(userRow);
	}

	@Test
	void testSavesOkOrFailedAndNeedsAConnection() {
		setUpApp();
		assertEquals(BaseError.GMAIL_NOT_CONNECTED, codeOf(service::test));

		String state = issuedState();
		when(client.exchange(anyString(), anyString(), anyString(), anyString())).thenReturn(new GmailClient.Tokens("the-refresh-token", "the-access-token"));
		when(client.mailboxAddress(API, "the-access-token")).thenReturn("karthi@gmail.com");
		service.connectFinish(finish("the-code", state, REDIRECT));

		when(client.accessToken("client-id-1234", "client-secret-xyz", "the-refresh-token")).thenReturn("fresh-access");
		when(client.mailboxAddress(API, "fresh-access")).thenReturn("karthi@gmail.com");
		assertEquals("ok", service.test().getStatus());

		when(client.accessToken(anyString(), anyString(), anyString())).thenThrow(new IllegalStateException("Google refused the request (invalid_grant)"));
		GmailStatusDto failed = service.test();
		assertEquals("failed", failed.getStatus());
		assertTrue(failed.isConnected(), "a failed check keeps the row so the user can reconnect");
	}

	@Test
	void disconnectTakesTheGrantBackAndRemovesTheRowAndIsSafeToRepeat() {
		setUpApp();
		String state = issuedState();
		when(client.exchange(anyString(), anyString(), anyString(), anyString())).thenReturn(new GmailClient.Tokens("the-refresh-token", "the-access-token"));
		when(client.mailboxAddress(anyString(), anyString())).thenReturn("karthi@gmail.com");
		service.connectFinish(finish("the-code", state, REDIRECT));

		assertTrue(service.disconnect());
		verify(client).revoke("the-refresh-token");
		assertNull(userRow);
		assertFalse(service.status().isConnected());
		assertTrue(service.disconnect());
		verify(client, org.mockito.Mockito.times(1)).revoke(anyString());
	}

	@Test
	void disconnectingKeepsTheSavedStatementPasswordsAndANewConnectionUsesThemAgain() {
		connected();
		GmailStatementSettingsRequestDto request = passwords("ABCDE1234F", "01011990");
		request.setName4("TEST");
		service.statementSave(request);

		assertTrue(service.disconnect());
		verify(client).revoke("the-refresh-token");
		assertNotNull(userRow, "the row stays for the passwords");
		assertFalse(userRow.isEnabled());
		GmailStatusDto status = service.status();
		assertFalse(status.isConnected(), "Gmail itself is disconnected");
		String kept = cipher.decrypt(userRow.getCredentialEnc());
		assertFalse(kept.contains("the-refresh-token"), "the token is gone");
		assertTrue(kept.contains("ABCDE1234F") && kept.contains("01011990") && kept.contains("TEST"), "the passwords are kept");
		var shown = service.statementReveal();
		assertEquals("ABCDE1234F", shown.getPan());
		assertEquals("01011990", shown.getDob());

		String state = issuedState();
		when(client.exchange(anyString(), anyString(), anyString(), anyString())).thenReturn(new GmailClient.Tokens("a-new-refresh-token", "a-new-access-token"));
		when(client.mailboxAddress(anyString(), anyString())).thenReturn("karthi@gmail.com");
		GmailStatusDto again = service.connectFinish(finish("the-code", state, REDIRECT));
		assertTrue(again.isConnected() && again.isPanSaved() && again.isDobSaved() && again.isNameSaved(), "connected again with the same passwords, nothing to type again");

		assertTrue(service.disconnect());
		assertTrue(service.disconnect());
		verify(client, org.mockito.Mockito.times(2)).revoke(anyString());
	}

	@Test
	void aUserNeverSeesAnotherUsersConnection() {
		setUpApp();
		String state = issuedState();
		when(client.exchange(anyString(), anyString(), anyString(), anyString())).thenReturn(new GmailClient.Tokens("the-refresh-token", "the-access-token"));
		when(client.mailboxAddress(anyString(), anyString())).thenReturn("karthi@gmail.com");
		service.connectFinish(finish("the-code", state, REDIRECT));

		RequestHeaderUtil.setUserId("user-2");
		assertFalse(service.status().isConnected());
		assertNull(service.status().getEmail());
		assertEquals(BaseError.GMAIL_NOT_CONNECTED, codeOf(service::test));
	}

	// ---- reading bank alerts (made-up alert text, never real mail) ----

	private static final String SLICE_CREDIT = "Hi TEST, You have received ₹300 via UPI in your slice bank account xx3333. Avl. Bal. ₹40,535.49 Transaction date 18-Nov-26 From Ms Someone RRN 222";
	private static final String HDFC_DEBIT = "Dear Customer, Rs.1250.00 is debited from your account ending 1111 towards VPA shop@bank on 03-11-26. UPI transaction reference no.: 1";

	private void connected() {
		setUpApp();
		String state = issuedState();
		when(client.exchange(anyString(), anyString(), anyString(), anyString())).thenReturn(new GmailClient.Tokens("the-refresh-token", "the-access-token"));
		when(client.mailboxAddress(anyString(), anyString())).thenReturn("karthi@gmail.com");
		service.connectFinish(finish("the-code", state, REDIRECT));
		when(client.accessToken("client-id-1234", "client-secret-xyz", "the-refresh-token")).thenReturn("fresh-access");
	}

	@Test
	void readingAlertsSavesEachTransactionOnceForReviewAndShowsAStatedBalanceWithoutSavingIt() {
		connected();
		when(client.messageIds(eq(API), eq("fresh-access"), anyString(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(List.of("m1", "m2", "m3", "m4"));
		when(client.message(API, "fresh-access", "m1")).thenReturn(new GmailClient.Message("alerts@hdfcbank.bank.in", HDFC_DEBIT));
		when(client.message(API, "fresh-access", "m2")).thenReturn(new GmailClient.Message("noreply@slice.bank.in", SLICE_CREDIT));
		when(client.message(API, "fresh-access", "m3")).thenReturn(new GmailClient.Message("alerts@hdfcbank.bank.in", "Your OTP is 123456 for a transaction"));
		when(client.message(API, "fresh-access", "m4")).thenReturn(new GmailClient.Message("noreply@slice.bank.in", SLICE_CREDIT));
		when(transactions.findAccountIdsByLastDigits("user-1", "3333", "slice")).thenReturn(List.of("account-slice"));
		when(transactions.findAccountIdsByLastDigits("user-1", "1111", "HDFC")).thenReturn(List.of("a-one", "a-two"));
		when(transactions.insert(any())).thenAnswer(i -> {
			WealthTransactionEntity entity = i.getArgument(0);
			return "gmail:m4".equals(entity.getExternalRef()) ? null : entity;
		});

		GmailFetchResultDto result = service.fetch();

		assertEquals(4, result.getRead());
		assertEquals(2, result.getAdded());
		assertEquals(1, result.getDuplicates());
		assertEquals(1, result.getSkipped());
		assertEquals(List.of("slice ••3333: ₹40535.49 on 2026-11-18"), result.getBalancesSeen());
		assertEquals(List.of("slice ••3333: ₹40535.49 on 2026-11-18"), service.status().getLastBalances(), "kept for next time");
		assertTrue(service.status().getLastBalancesAt() != null);
		ArgumentCaptor<WealthTransactionEntity> saved = ArgumentCaptor.forClass(WealthTransactionEntity.class);
		verify(transactions, org.mockito.Mockito.times(3)).insert(saved.capture());
		WealthTransactionEntity hdfc = saved.getAllValues().get(0);
		assertEquals("gmail:m1", hdfc.getExternalRef());
		assertEquals("expense", hdfc.getKind());
		assertEquals("1250.00", hdfc.getAmount().toPlainString());
		assertEquals("2026-11-03", hdfc.getOccurredOn());
		assertNull(hdfc.getAccountId(), "two accounts end in those digits, so it stays unassigned");
		WealthTransactionEntity slice = saved.getAllValues().get(1);
		assertEquals("income", slice.getKind());
		assertEquals("account-slice", slice.getAccountId());
		assertEquals("ok", userRow.getLastStatus());
		verify(nexus).evict("transactions", "user-1");
	}

	// ---- statement passwords and statement PDFs (fake values and generated PDFs only) ----

	private GmailStatementSettingsRequestDto passwords(String pan, String dob) {
		GmailStatementSettingsRequestDto request = new GmailStatementSettingsRequestDto();
		request.setPan(pan);
		request.setDob(dob);
		return request;
	}

	@Test
	void statementPasswordsAreKeptEncryptedWithTheTokenAndOnlyTheirPresenceIsShown() {
		connected();
		assertFalse(service.status().isStatementPasswordsSet());

		GmailStatusDto saved = service.statementSave(passwords("abcde1234f", ""));
		assertTrue(saved.isPanSaved() && !saved.isDobSaved(), "each one shows on its own whether it is saved");
		saved = service.statementSave(passwords("abcde1234f", "01011990"));
		assertTrue(saved.isPanSaved() && saved.isDobSaved());
		assertTrue(saved.isStatementPasswordsSet());
		assertEquals("ok", saved.getStatus(), "saving passwords does not reset the last check");
		String plain = cipher.decrypt(userRow.getCredentialEnc());
		assertTrue(plain.contains("ABCDE1234F") && plain.contains("01011990") && plain.contains("the-refresh-token"));
		assertFalse(userRow.getCredentialEnc().contains("ABCDE1234F"));

		service.statementSave(passwords("", "02021991"));
		String again = cipher.decrypt(userRow.getCredentialEnc());
		assertTrue(again.contains("ABCDE1234F") && again.contains("02021991") && !again.contains("01011990"), "a blank value keeps the saved one");

		GmailStatusDto clearedStatus = service.statementClear();
		assertFalse(clearedStatus.isStatementPasswordsSet() || clearedStatus.isPanSaved() || clearedStatus.isDobSaved());
		String cleared = cipher.decrypt(userRow.getCredentialEnc());
		assertFalse(cleared.contains("ABCDE1234F") || cleared.contains("02021991"));
		assertTrue(cleared.contains("the-refresh-token"), "the Gmail token stays");
	}

	@Test
	void badPasswordsAreRefusedWithoutEchoingThemAndANewConnectionKeepsTheSavedOnes() {
		connected();
		for (GmailStatementSettingsRequestDto bad : new GmailStatementSettingsRequestDto[] { passwords("ABCDE1234", ""), passwords("abc", ""), passwords("", "1011990"), passwords("", "31021990"),
				passwords("", "01012999"), passwords("", "ab011990"), passwords("", ""), passwords(null, null) }) {
			KarthiException e = assertThrows(KarthiException.class, () -> service.statementSave(bad));
			assertEquals(BaseError.GMAIL_REQUEST_INVALID, e.getMessage());
		}
		service.statementSave(passwords("ABCDE1234F", "01011990"));

		String state = issuedState();
		when(client.exchange(anyString(), anyString(), anyString(), anyString())).thenReturn(new GmailClient.Tokens("a-new-refresh-token", "a-new-access-token"));
		when(client.mailboxAddress(anyString(), anyString())).thenReturn("karthi@gmail.com");
		GmailStatusDto status = service.connectFinish(finish("the-code", state, REDIRECT));
		assertTrue(status.isStatementPasswordsSet());
		String plain = cipher.decrypt(userRow.getCredentialEnc());
		assertTrue(plain.contains("a-new-refresh-token") && plain.contains("ABCDE1234F") && plain.contains("01011990"));
	}

	@Test
	void statementsAreOpenedInMemoryAndOnlyLabelledFiguresAreReturned() throws java.io.IOException {
		connected();
		assertEquals(BaseError.GMAIL_REQUEST_INVALID, codeOf(service::statements), "no statement passwords saved yet");
		service.statementSave(passwords("ABCDE1234F", "01011990"));

		byte[] good = com.wealth.bss.config.StatementReaderTest.pdf(java.util.List.of("Savings statement", "Closing Balance Rs. 40,000.00 Cr"), "01011990");
		byte[] stranger = com.wealth.bss.config.StatementReaderTest.pdf(java.util.List.of("Closing Balance Rs. 1.00"), "99999999");
		when(client.messageIds(eq(API), eq("fresh-access"), anyString(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(java.util.List.of("m1", "m2"));
		when(client.pdfAttachments(API, "fresh-access", "m1")).thenReturn(java.util.List.of(new GmailClient.Attachment("slice_statement_Oct_2026.pdf", "att-one-0000001")));
		when(client.pdfAttachments(API, "fresh-access", "m2")).thenReturn(java.util.List.of(new GmailClient.Attachment("other_statement_123456789012.pdf", "att-two-0000002")));
		when(client.attachment(API, "fresh-access", "m1", "att-one-0000001")).thenReturn(good);
		when(client.attachment(API, "fresh-access", "m2", "att-two-0000002")).thenReturn(stranger);

		var result = service.statements();

		assertEquals(2, result.getRead());
		assertEquals(1, result.getOpened());
		assertEquals(1, result.getLocked());
		assertEquals(java.util.List.of("other_statement_…"), result.getLockedNames(), "the locked one is named, with the long number hidden");
		assertEquals(java.util.List.of("slice_statement_Oct_2026: Closing Balance ₹40,000.00 Cr"), result.getFigures());
		verify(transactions, never()).insert(any());

		// The opened statement's figures are saved as one JSON object (label to value); the locked one is kept as a history row with no figures.
		ArgumentCaptor<com.wealth.bss.common.model.entity.WealthStatementFigureEntity> savedFigures = ArgumentCaptor.forClass(com.wealth.bss.common.model.entity.WealthStatementFigureEntity.class);
		verify(statements, org.mockito.Mockito.times(2)).upsert(savedFigures.capture());
		com.wealth.bss.common.model.entity.WealthStatementFigureEntity first = savedFigures.getAllValues().get(0);
		assertEquals("user-1", first.getUserId());
		assertEquals("m1", first.getMailRef());
		assertEquals("slice_statement_Oct_2026", first.getSource());
		assertEquals("{\"Closing Balance\":\"₹40,000.00 Cr\"}", first.getFigures());
		assertEquals("opened", first.getStatus());
		assertEquals("gmail", first.getOrigin());
		com.wealth.bss.common.model.entity.WealthStatementFigureEntity second = savedFigures.getAllValues().get(1);
		assertEquals("locked", second.getStatus());
		assertEquals("{}", second.getFigures());
		assertEquals("other_statement_123456789012.pdf", second.getFileName());

		GmailStatusDto next = service.status();
		assertEquals(java.util.List.of("slice_statement_Oct_2026: Closing Balance ₹40,000.00 Cr"), next.getLastFigures(), "kept for next time");
		assertEquals(1, next.getLastStatementsOpened());
		assertEquals(1, next.getLastStatementsLocked());
		assertTrue(next.getLastFiguresAt() != null);
		String plain = cipher.decrypt(userRow.getCredentialEnc());
		assertTrue(plain.contains("ABCDE1234F") && plain.contains("the-refresh-token"), "the token and the passwords are kept with it");
		assertFalse(userRow.getCredentialEnc().contains("Closing Balance"), "the saved block is encrypted");
	}

	@Test
	void anHdfcCardStatementOpensWithTheNameLettersAndTheDayAndMonthOfBirth() throws java.io.IOException {
		connected();
		GmailStatementSettingsRequestDto request = passwords("", "05051990");
		request.setName4("test");
		GmailStatusDto saved = service.statementSave(request);
		assertTrue(saved.isNameSaved() && saved.isDobSaved());
		assertEquals(BaseError.GMAIL_REQUEST_INVALID, codeOf(() -> {
			GmailStatementSettingsRequestDto bad = passwords("", "");
			bad.setName4("TES");
			service.statementSave(bad);
		}), "four letters only");

		byte[] card = com.wealth.bss.config.StatementReaderTest.pdf(java.util.List.of("Closing Balance Rs. 10.00"), "TEST0505");
		when(client.messageIds(eq(API), eq("fresh-access"), anyString(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(java.util.List.of("m1"));
		when(client.pdfAttachments(API, "fresh-access", "m1")).thenReturn(java.util.List.of(new GmailClient.Attachment("card.pdf", "att-card-0000001")));
		when(client.attachment(API, "fresh-access", "m1", "att-card-0000001")).thenReturn(card);

		var result = service.statements();
		assertEquals(1, result.getOpened());
		assertEquals(0, result.getLocked());

		service.statementClear();
		assertFalse(service.status().isNameSaved());
	}

	// A statement's rows are kept with it when it is read; nothing reaches Transactions until the user accepts the statement.
	private com.wealth.bss.common.model.entity.WealthStatementFigureEntity readCardStatement(java.util.List<String> rows) throws java.io.IOException {
		connected();
		service.statementSave(passwords("ABCDE1234F", ""));
		java.util.List<String> lines = new java.util.ArrayList<>();
		lines.add("Statement Date 22 Nov, 2026");
		lines.addAll(rows);
		byte[] card = com.wealth.bss.config.StatementReaderTest.pdf(lines, "ABCDE1234F");
		when(client.messageIds(eq(API), eq("fresh-access"), anyString(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(java.util.List.of("m1"));
		when(client.pdfAttachments(API, "fresh-access", "m1")).thenReturn(java.util.List.of(new GmailClient.Attachment("card.pdf", "att-card-0000001")));
		when(client.attachment(API, "fresh-access", "m1", "att-card-0000001")).thenReturn(card);
		var result = service.statements();
		assertEquals(rows.size(), result.getRows());
		ArgumentCaptor<com.wealth.bss.common.model.entity.WealthStatementFigureEntity> saved = ArgumentCaptor.forClass(com.wealth.bss.common.model.entity.WealthStatementFigureEntity.class);
		verify(statements, org.mockito.Mockito.atLeastOnce()).upsert(saved.capture());
		com.wealth.bss.common.model.entity.WealthStatementFigureEntity row = saved.getValue();
		row.setId("11111111-1111-1111-1111-111111111111");
		row.setStatus("opened");
		when(statements.find("user-1", "11111111-1111-1111-1111-111111111111")).thenReturn(row);
		return row;
	}

	@Test
	void readingAStatementKeepsItsRowsAndSavesNoTransactionUntilTheUserAccepts() throws java.io.IOException {
		var row = readCardStatement(java.util.List.of("23/10/2026| 00:00 TEST SHOP C 99.00 l", "24/10/2026| 10:09 PAYMENT RECEIVED + C 500.00 l", "25/10/2026| 11:00 ANOTHER SHOP C 12.50 l"));

		verify(transactions, never()).insert(any());
		assertTrue(row.getLines().contains("TEST SHOP") && row.getLines().contains("\"time\":\"10:09\"") && row.getLines().contains("\"debit\":false"), "the date, time and direction are kept");
		verify(statements, never()).markAccepted(anyString(), anyString());
	}

	@Test
	void acceptingSplitsTheStatementIntoTransactionsInDateAndTimeOrderChecksEachAndSavesThemOnce() throws java.io.IOException {
		var row = readCardStatement(java.util.List.of("25/10/2026| 11:00 SHOP B C 12.50 l", "23/10/2026| 09:00 SHOP A C 99.00 l", "23/10/2026| 18:30 SHOP C + C 500.00 l"));
		java.util.Set<String> refs = new java.util.HashSet<>();
		java.util.List<String> order = new java.util.ArrayList<>();
		when(transactions.insert(any())).thenAnswer(i -> {
			com.wealth.bss.common.model.entity.WealthTransactionEntity entity = i.getArgument(0);
			if (!refs.add(entity.getExternalRef())) {
				return null;
			}
			order.add(entity.getDescription());
			return entity;
		});
		var accept = new com.wealth.common.model.dto.admin.StatementAcceptRequestDto();
		accept.setId("11111111-1111-1111-1111-111111111111");

		var first = service.statementAccept(accept);

		assertEquals(3, first.getStored() + first.getSame() + first.getReview() + first.getRepeated(), "every row is accounted for");
		assertEquals(3, first.getStored());
		assertEquals(java.util.List.of("SHOP A", "SHOP C", "SHOP B"), order, "oldest first by date and time");
		assertTrue(refs.stream().allMatch(ref -> ref.startsWith("stmt:")));
		verify(statements).markAccepted("user-1", "11111111-1111-1111-1111-111111111111");

		var again = service.statementAccept(accept);
		assertEquals(3, again.getStored() + again.getSame() + again.getReview() + again.getRepeated(), "accepted again, still every row is accounted for");
		assertEquals(0, again.getStored(), "nothing is saved twice");
		assertEquals(3, refs.size());
	}

	@Test
	void aStatementThatIsNotTheCallersOrDidNotOpenCannotBeAccepted() throws java.io.IOException {
		var row = readCardStatement(java.util.List.of("23/10/2026| 09:00 SHOP A C 99.00 l"));
		var request = new com.wealth.common.model.dto.admin.StatementAcceptRequestDto();
		request.setId("22222222-2222-2222-2222-222222222222");
		assertEquals(BaseError.GMAIL_REQUEST_INVALID, codeOf(() -> service.statementAccept(request)), "not found for this user");
		for (String bad : new String[] { "", "1; drop", null }) {
			var odd = new com.wealth.common.model.dto.admin.StatementAcceptRequestDto();
			odd.setId(bad);
			assertEquals(BaseError.GMAIL_REQUEST_INVALID, codeOf(() -> service.statementAccept(odd)));
		}
		assertEquals(BaseError.GMAIL_REQUEST_INVALID, codeOf(() -> service.statementAccept(null)));

		row.setStatus("locked");
		var locked = new com.wealth.common.model.dto.admin.StatementAcceptRequestDto();
		locked.setId("11111111-1111-1111-1111-111111111111");
		assertEquals(BaseError.GMAIL_REQUEST_INVALID, codeOf(() -> service.statementAccept(locked)), "a locked statement has no rows to accept");
		verify(transactions, never()).insert(any());
		verify(statements, never()).markAccepted(anyString(), anyString());
	}

	@Test
	void theSavedStatementsAreSplitBackIntoLabelAndValuePairs() {
		com.wealth.bss.common.model.entity.WealthStatementFigureEntity row = new com.wealth.bss.common.model.entity.WealthStatementFigureEntity();
		row.setSource("hdfc_card_Nov_2026");
		row.setFigures("{\"Total Amount Due\":\"₹250.75\",\"Due Date\":\"12 Dec, 2026\"}");
		row.setReadAt(new java.sql.Timestamp(0));
		com.wealth.bss.common.model.entity.WealthStatementFigureEntity broken = new com.wealth.bss.common.model.entity.WealthStatementFigureEntity();
		broken.setSource("odd");
		broken.setFigures("not json");
		when(statements.findByUser("user-1")).thenReturn(java.util.List.of(row, broken));

		var list = service.statementList();

		assertEquals(2, list.size());
		assertEquals("hdfc_card_Nov_2026", list.get(0).getSource());
		assertEquals(java.util.List.of("Total Amount Due", "Due Date"), new java.util.ArrayList<>(list.get(0).getFigures().keySet()));
		assertEquals("₹250.75", list.get(0).getFigures().get("Total Amount Due"));
		assertTrue(list.get(1).getFigures().isEmpty(), "a row that cannot be read shows no figures instead of failing");
		RequestHeaderUtil.setUserId("user-2");
		service.statementList();
		verify(statements).findByUser("user-2");
	}

	@Test
	void theSavedPasswordsAreShownOnlyToTheirOwnerOnRequestAndNeverInTheStatus() {
		assertEquals(BaseError.GMAIL_NOT_CONNECTED, codeOf(service::statementReveal));
		connected();
		var none = service.statementReveal();
		assertEquals("", none.getPan() + none.getDob() + none.getName4(), "nothing saved: nothing shown");

		GmailStatementSettingsRequestDto request = passwords("abcde1234f", "01011990");
		request.setName4("test");
		service.statementSave(request);
		var shown = service.statementReveal();
		assertEquals("ABCDE1234F", shown.getPan());
		assertEquals("01011990", shown.getDob());
		assertEquals("TEST", shown.getName4());

		GmailStatusDto status = service.status();
		assertTrue(status.isPanSaved() && status.isDobSaved() && status.isNameSaved());
		assertFalse(status.toString().contains("ABCDE1234F") || status.getEmail().contains("1990"), "the status carries only that they are saved");

		RequestHeaderUtil.setUserId("user-2");
		assertEquals(BaseError.GMAIL_NOT_CONNECTED, codeOf(service::statementReveal), "another user sees nothing of it");
	}

	@Test
	void aGoogleFailureWhileReadingStatementsIsReported() {
		connected();
		service.statementSave(passwords("ABCDE1234F", ""));
		when(client.messageIds(anyString(), anyString(), anyString(), org.mockito.ArgumentMatchers.anyInt())).thenThrow(new IllegalStateException("Google refused the request"));
		assertEquals(BaseError.GMAIL_FETCH_FAILED, codeOf(service::statements));
	}

	@Test
	void readingNeedsAConnectionAndAGoogleFailureIsReportedAndMarkedFailed() {
		setUpApp();
		assertEquals(BaseError.GMAIL_NOT_CONNECTED, codeOf(service::fetch));

		connected();
		when(client.messageIds(anyString(), anyString(), anyString(), org.mockito.ArgumentMatchers.anyInt())).thenThrow(new IllegalStateException("Google refused the request (invalid_grant)"));
		assertEquals(BaseError.GMAIL_FETCH_FAILED, codeOf(service::fetch));
		assertEquals("failed", userRow.getLastStatus());
		verify(transactions, never()).insert(any());
	}

	@Test
	void theReviewListIsTheCallersOwnAndADecisionChangesOnlyAPendingGmailRow() {
		WealthTransactionEntity row = new WealthTransactionEntity();
		row.setId("11111111-1111-1111-1111-111111111111");
		row.setKind("expense");
		row.setAmount(new java.math.BigDecimal("40.00"));
		row.setOccurredOn("2026-11-22");
		row.setDescription("SOME PAYEE");
		row.setAccount("Unassigned");
		when(transactions.findPendingFromGmail("user-1")).thenReturn(List.of(row));
		var items = service.pending();
		assertEquals(1, items.size());
		assertEquals("", items.get(0).getAccount(), "no matched account shows as empty");
		assertEquals("SOME PAYEE", items.get(0).getDescription());

		when(transactions.setGmailReviewStatus("user-1", "11111111-1111-1111-1111-111111111111", "accepted")).thenReturn(true);
		GmailReviewRequestDto accept = new GmailReviewRequestDto();
		accept.setId("11111111-1111-1111-1111-111111111111");
		accept.setDecision("accept");
		assertTrue(service.review(accept));
		verify(nexus).evict("transactions", "user-1");

		GmailReviewRequestDto none = new GmailReviewRequestDto();
		none.setId("11111111-1111-1111-1111-111111111111");
		none.setDecision("dismiss");
		assertEquals(BaseError.GMAIL_REQUEST_INVALID, codeOf(() -> service.review(none)), "nothing pending to change");
		for (String bad : new String[] { "delete", "Accept", "", null }) {
			GmailReviewRequestDto request = new GmailReviewRequestDto();
			request.setId("11111111-1111-1111-1111-111111111111");
			request.setDecision(bad);
			assertEquals(BaseError.GMAIL_REQUEST_INVALID, codeOf(() -> service.review(request)));
		}
		GmailReviewRequestDto badId = new GmailReviewRequestDto();
		badId.setId("1; drop table");
		badId.setDecision("accept");
		assertEquals(BaseError.GMAIL_REQUEST_INVALID, codeOf(() -> service.review(badId)));
	}

	@Test
	void statementsLockedWithOtherCommonFormsOfWhatTheUserSavedAreOpenedToo() throws java.io.IOException {
		connected();
		GmailStatementSettingsRequestDto saved = passwords("ABCDE1234F", "15071998");
		saved.setName4("RAVI");
		service.statementSave(saved);
		String[] forms = { "150798", "1507", "abcde1234f", "RAVI1507", "ravi1507" };
		java.util.List<String> ids = new java.util.ArrayList<>();
		for (int i = 0; i < forms.length; i++) {
			String id = "m" + i;
			ids.add(id);
			when(client.pdfAttachments(API, "fresh-access", id)).thenReturn(java.util.List.of(new GmailClient.Attachment("bank_" + i + "_statement.pdf", "att-" + i + "-0000000")));
			when(client.attachment(API, "fresh-access", id, "att-" + i + "-0000000"))
					.thenReturn(com.wealth.bss.config.StatementReaderTest.pdf(java.util.List.of("Closing Balance Rs. " + (i + 1) + ".00 Cr"), forms[i]));
		}
		when(client.messageIds(eq(API), eq("fresh-access"), anyString(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(ids);

		var result = service.statements();

		assertEquals(5, result.getOpened(), "every form of the saved details opens its statement");
		assertEquals(0, result.getLocked());
		assertTrue(result.getLockedNames().isEmpty());
	}

	private static com.wealth.common.model.dto.admin.StatementUploadRequestDto upload(String name, byte[] bytes, String password, boolean keep) {
		com.wealth.common.model.dto.admin.StatementUploadRequestDto request = new com.wealth.common.model.dto.admin.StatementUploadRequestDto();
		request.setFileName(name);
		request.setData(java.util.Base64.getEncoder().encodeToString(bytes));
		request.setPassword(password);
		request.setKeep(keep);
		return request;
	}

	@Test
	void anUploadedStatementOpensWithTheSavedPasswordAndIsKeptAsAnUploadRow() throws java.io.IOException {
		connected();
		service.statementSave(passwords("ABCDE1234F", "01011990"));
		byte[] pdf = com.wealth.bss.config.StatementReaderTest.pdf(java.util.List.of("Closing Balance Rs. 40,000.00 Cr"), "01011990");

		var result = service.statementUpload(upload("my_statement.pdf", pdf, null, false));

		assertEquals(1, result.getOpened());
		assertEquals(0, result.getLocked());
		ArgumentCaptor<com.wealth.bss.common.model.entity.WealthStatementFigureEntity> saved = ArgumentCaptor.forClass(com.wealth.bss.common.model.entity.WealthStatementFigureEntity.class);
		verify(statements).upsert(saved.capture());
		assertEquals("upload", saved.getValue().getOrigin());
		assertEquals("opened", saved.getValue().getStatus());
		assertEquals("my_statement.pdf", saved.getValue().getFileName());
		assertTrue(saved.getValue().getMailRef().startsWith("upload:"));
		assertTrue(saved.getValue().getMailRef().length() <= 40);
		verify(client, never()).attachment(anyString(), anyString(), anyString(), anyString());
	}

	@Test
	void anUploadedStatementThatStaysLockedIsAHistoryRowAndATypedPasswordIsKeptOnlyWhenAsked() throws java.io.IOException {
		connected();
		service.statementSave(passwords("ABCDE1234F", "01011990"));
		byte[] pdf = com.wealth.bss.config.StatementReaderTest.pdf(java.util.List.of("Closing Balance Rs. 5.00 Cr"), "secret99");

		var locked = service.statementUpload(upload("locked.pdf", pdf, "wrong-one", true));
		assertEquals(1, locked.getLocked());
		assertFalse(cipher.decrypt(userRow.getCredentialEnc()).contains("wrong-one"), "a password that did not open the file is never kept");

		var once = service.statementUpload(upload("locked.pdf", pdf, "secret99", false));
		assertEquals(1, once.getOpened());
		assertFalse(cipher.decrypt(userRow.getCredentialEnc()).contains("secret99"), "used once, not kept");

		var kept = service.statementUpload(upload("locked.pdf", pdf, "secret99", true));
		assertEquals(1, kept.getOpened());
		assertTrue(cipher.decrypt(userRow.getCredentialEnc()).contains("secret99"));

		var later = service.statementUpload(upload("locked.pdf", pdf, null, false));
		assertEquals(1, later.getOpened(), "the kept password opens it next time");
		ArgumentCaptor<com.wealth.bss.common.model.entity.WealthStatementFigureEntity> saved = ArgumentCaptor.forClass(com.wealth.bss.common.model.entity.WealthStatementFigureEntity.class);
		verify(statements, org.mockito.Mockito.times(4)).upsert(saved.capture());
		assertEquals("locked", saved.getAllValues().get(0).getStatus());
		assertEquals("{}", saved.getAllValues().get(0).getFigures());
	}

	@Test
	void anUploadThatIsNotAPdfOrTooBigOrBadlyNamedIsRefused() {
		connected();
		byte[] text = "hello world, not a pdf".getBytes(java.nio.charset.StandardCharsets.UTF_8);
		assertEquals(BaseError.GMAIL_REQUEST_INVALID, codeOf(() -> service.statementUpload(upload("a.pdf", text, null, false))), "no PDF header");
		assertEquals(BaseError.GMAIL_REQUEST_INVALID, codeOf(() -> service.statementUpload(upload("a.txt", "%PDF-1.4".getBytes(), null, false))), "not a .pdf name");
		assertEquals(BaseError.GMAIL_REQUEST_INVALID, codeOf(() -> service.statementUpload(upload("x".repeat(200) + ".pdf", "%PDF-1.4".getBytes(), null, false))), "name too long");
		assertEquals(BaseError.GMAIL_REQUEST_INVALID, codeOf(() -> service.statementUpload(upload("a.pdf", "%PDF-1.4".getBytes(), "p".repeat(101), false))), "password too long");
		byte[] big = new byte[5_000_001];
		big[0] = '%';
		big[1] = 'P';
		big[2] = 'D';
		big[3] = 'F';
		assertEquals(BaseError.GMAIL_REQUEST_INVALID, codeOf(() -> service.statementUpload(upload("big.pdf", big, null, false))), "over 5 MB");
		com.wealth.common.model.dto.admin.StatementUploadRequestDto notBase64 = upload("a.pdf", text, null, false);
		notBase64.setData("***not base64***");
		assertEquals(BaseError.GMAIL_REQUEST_INVALID, codeOf(() -> service.statementUpload(notBase64)));
		assertEquals(BaseError.GMAIL_REQUEST_INVALID, codeOf(() -> service.statementUpload(null)));
		verify(statements, never()).upsert(any());
	}

	@Test
	void aLockedGmailStatementOpensAgainWithATypedPasswordAndOnlyThatOneIsTried() throws java.io.IOException {
		connected();
		service.statementSave(passwords("ABCDE1234F", "01011990"));
		String id = "11111111-1111-1111-1111-111111111111";
		com.wealth.bss.common.model.entity.WealthStatementFigureEntity row = new com.wealth.bss.common.model.entity.WealthStatementFigureEntity();
		row.setId(id);
		row.setMailRef("m1");
		row.setSource("bank_statement");
		row.setFileName("bank_statement.pdf");
		row.setOrigin("gmail");
		row.setStatus("locked");
		row.setFigures("{}");
		when(statements.find("user-1", id)).thenReturn(row);
		when(client.pdfAttachments(API, "fresh-access", "m1")).thenReturn(java.util.List.of(new GmailClient.Attachment("bank_statement.pdf", "att-one-0000001"), new GmailClient.Attachment("other.pdf", "att-two-0000002")));
		when(client.attachment(API, "fresh-access", "m1", "att-one-0000001")).thenReturn(com.wealth.bss.config.StatementReaderTest.pdf(java.util.List.of("Closing Balance Rs. 9.00 Cr"), "typed-pass"));
		com.wealth.common.model.dto.admin.StatementRetryRequestDto request = new com.wealth.common.model.dto.admin.StatementRetryRequestDto();
		request.setId(id);
		request.setPassword("wrong");

		assertEquals(1, service.statementRetry(request).getLocked());
		request.setPassword("typed-pass");
		request.setKeep(true);
		var result = service.statementRetry(request);

		assertEquals(1, result.getOpened());
		assertTrue(cipher.decrypt(userRow.getCredentialEnc()).contains("typed-pass"));
		verify(client, never()).attachment(API, "fresh-access", "m1", "att-two-0000002");
		ArgumentCaptor<com.wealth.bss.common.model.entity.WealthStatementFigureEntity> saved = ArgumentCaptor.forClass(com.wealth.bss.common.model.entity.WealthStatementFigureEntity.class);
		verify(statements, org.mockito.Mockito.times(2)).upsert(saved.capture());
		assertEquals("opened", saved.getAllValues().get(1).getStatus());
		assertEquals("m1", saved.getAllValues().get(1).getMailRef());
	}

	@Test
	void aRetryIsRefusedForAStatementThatIsNotTheUsersNotLockedOrUploaded() {
		connected();
		String id = "22222222-2222-2222-2222-222222222222";
		com.wealth.common.model.dto.admin.StatementRetryRequestDto request = new com.wealth.common.model.dto.admin.StatementRetryRequestDto();
		request.setId(id);
		request.setPassword("x");
		assertEquals(BaseError.GMAIL_REQUEST_INVALID, codeOf(() -> service.statementRetry(request)), "not the user's");
		com.wealth.bss.common.model.entity.WealthStatementFigureEntity row = new com.wealth.bss.common.model.entity.WealthStatementFigureEntity();
		row.setId(id);
		row.setMailRef("m1");
		row.setFileName("a.pdf");
		row.setOrigin("gmail");
		row.setStatus("opened");
		when(statements.find("user-1", id)).thenReturn(row);
		assertEquals(BaseError.GMAIL_REQUEST_INVALID, codeOf(() -> service.statementRetry(request)), "already opened");
		row.setStatus("locked");
		row.setOrigin("upload");
		assertEquals(BaseError.GMAIL_REQUEST_INVALID, codeOf(() -> service.statementRetry(request)), "an uploaded file is not kept");
		request.setId("not-an-id");
		assertEquals(BaseError.GMAIL_REQUEST_INVALID, codeOf(() -> service.statementRetry(request)));
		request.setId(id);
		request.setPassword("");
		assertEquals(BaseError.GMAIL_REQUEST_INVALID, codeOf(() -> service.statementRetry(request)), "a password is needed");
	}

	@Test
	void aGmailStatementIsFetchedFromGmailForDownloadAndAnythingElseIsRefused() throws java.io.IOException {
		connected();
		String id = "33333333-3333-3333-3333-333333333333";
		com.wealth.bss.common.model.entity.WealthStatementFigureEntity row = new com.wealth.bss.common.model.entity.WealthStatementFigureEntity();
		row.setId(id);
		row.setMailRef("m1");
		row.setFileName("bank_statement.pdf");
		row.setOrigin("gmail");
		row.setStatus("locked");
		when(statements.find("user-1", id)).thenReturn(row);
		byte[] pdf = com.wealth.bss.config.StatementReaderTest.pdf(java.util.List.of("Closing Balance Rs. 9.00 Cr"), "secret");
		when(client.pdfAttachments(API, "fresh-access", "m1")).thenReturn(java.util.List.of(new GmailClient.Attachment("other.pdf", "att-two-0000002"), new GmailClient.Attachment("bank_statement.pdf", "att-one-0000001")));
		when(client.attachment(API, "fresh-access", "m1", "att-one-0000001")).thenReturn(pdf);
		com.wealth.common.model.dto.admin.StatementDownloadRequestDto request = new com.wealth.common.model.dto.admin.StatementDownloadRequestDto();
		request.setId(id);

		var file = service.statementDownload(request);

		assertEquals("bank_statement.pdf", file.getFileName());
		assertTrue(java.util.Arrays.equals(pdf, java.util.Base64.getDecoder().decode(file.getData())));
		verify(client, never()).attachment(API, "fresh-access", "m1", "att-two-0000002");
		verify(statements, never()).upsert(any());

		row.setOrigin("upload");
		assertEquals(BaseError.GMAIL_REQUEST_INVALID, codeOf(() -> service.statementDownload(request)), "an uploaded file is not kept");
		row.setOrigin("gmail");
		when(client.pdfAttachments(API, "fresh-access", "m1")).thenReturn(java.util.List.of());
		assertEquals(BaseError.GMAIL_FETCH_FAILED, codeOf(() -> service.statementDownload(request)), "Gmail no longer has the file");
		request.setId("22222222-2222-2222-2222-222222222222");
		assertEquals(BaseError.GMAIL_REQUEST_INVALID, codeOf(() -> service.statementDownload(request)), "not the user's");
		request.setId("not an id");
		assertEquals(BaseError.GMAIL_REQUEST_INVALID, codeOf(() -> service.statementDownload(request)));
		assertEquals(BaseError.GMAIL_REQUEST_INVALID, codeOf(() -> service.statementDownload(null)));
	}

	private com.wealth.bss.common.model.entity.WealthConnectionEntity geminiRow(boolean enabled, String key) {
		com.wealth.bss.common.model.entity.WealthConnectionEntity row = new com.wealth.bss.common.model.entity.WealthConnectionEntity();
		row.setProvider("gemini");
		row.setBaseUrl("https://gemini.example");
		row.setEnabled(enabled);
		row.setCredentialEnc(key == null ? null : cipher.encrypt(key));
		return row;
	}

	@Test
	void aStatementWithNoKnownRowLayoutIsReadByTheAiWithPersonalDetailsHiddenAndItsRowsWaitNotSure() throws java.io.IOException {
		connected();
		GmailStatementSettingsRequestDto saved = passwords("ABCDE1234F", "01011990");
		saved.setName4("KART");
		service.statementSave(saved);
		byte[] pdf = com.wealth.bss.config.StatementReaderTest.pdf(java.util.List.of("KARTHIKEYAN Credit card", "Card 5280123412341234 PAN ABCDE1234F", "Closing Balance Rs. 10.00 Cr", "Google 149 19 Sep 26 UPI"), "01011990");
		when(client.messageIds(eq(API), eq("fresh-access"), anyString(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(java.util.List.of("m1"));
		when(client.pdfAttachments(API, "fresh-access", "m1")).thenReturn(java.util.List.of(new GmailClient.Attachment("card.pdf", "att-card-0000001")));
		when(client.attachment(API, "fresh-access", "m1", "att-card-0000001")).thenReturn(pdf);
		when(main.findAll()).thenAnswer(i -> java.util.List.of(mainRow, geminiRow(true, "gemini-key")));
		java.util.concurrent.atomic.AtomicReference<String> sent = new java.util.concurrent.atomic.AtomicReference<>();
		when(gemini.generateJson(anyString(), anyString(), anyString(), anyString())).thenAnswer(call -> {
			sent.set(call.getArgument(3));
			return "{\"rows\":[{\"day\":\"2026-09-19\",\"description\":\"Google\",\"amount\":149,\"direction\":\"debit\"},{\"day\":\"not-a-date\",\"description\":\"Bad\",\"amount\":5,\"direction\":\"debit\"},{\"day\":\"2026-09-18\",\"description\":\"Refund\",\"amount\":10.5,\"direction\":\"credit\"}]}";
		});

		var result = service.statements();

		assertEquals(1, result.getAiStatements());
		assertEquals(2, result.getRows(), "the row with a bad date is dropped");
		assertFalse(sent.get().contains("KARTHIKEYAN") || sent.get().contains("5280123412341234") || sent.get().contains("ABCDE1234F"), "name, card number and PAN are not sent");
		assertTrue(sent.get().contains("Google 149") && sent.get().contains("Closing Balance"), "the statement itself is sent");
		ArgumentCaptor<com.wealth.bss.common.model.entity.WealthStatementFigureEntity> kept = ArgumentCaptor.forClass(com.wealth.bss.common.model.entity.WealthStatementFigureEntity.class);
		verify(statements).upsert(kept.capture());
		assertTrue(kept.getValue().getLines().contains("\"sure\":false"), "AI rows are marked not sure, so accepting sends them to the review");
		assertTrue(kept.getValue().getLines().contains("Refund") && !kept.getValue().getLines().contains("Bad"));
		verify(transactions, never()).insert(any());
	}

	@Test
	void theAiIsNotCalledWithoutAnEnabledProviderWithAKeyAndAFailedCallLeavesNoRows() throws java.io.IOException {
		connected();
		service.statementSave(passwords("ABCDE1234F", "01011990"));
		byte[] pdf = com.wealth.bss.config.StatementReaderTest.pdf(java.util.List.of("Closing Balance Rs. 10.00 Cr"), "01011990");
		when(client.messageIds(eq(API), eq("fresh-access"), anyString(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(java.util.List.of("m1"));
		when(client.pdfAttachments(API, "fresh-access", "m1")).thenReturn(java.util.List.of(new GmailClient.Attachment("card.pdf", "att-card-0000001")));
		when(client.attachment(API, "fresh-access", "m1", "att-card-0000001")).thenReturn(pdf);

		when(main.findAll()).thenAnswer(i -> java.util.List.of(mainRow, geminiRow(false, "k")));
		assertEquals(0, service.statements().getRows(), "provider switched off");
		when(main.findAll()).thenAnswer(i -> java.util.List.of(mainRow, geminiRow(true, null)));
		assertEquals(0, service.statements().getRows(), "no key");
		verify(gemini, never()).generateJson(anyString(), anyString(), anyString(), anyString());

		when(main.findAll()).thenAnswer(i -> java.util.List.of(mainRow, geminiRow(true, "k")));
		when(gemini.generateJson(anyString(), anyString(), anyString(), anyString())).thenThrow(new IllegalStateException("Gemini refused the request (HTTP 429)"));
		var failed = service.statements();
		assertEquals(0, failed.getRows());
		assertEquals(1, failed.getOpened(), "the statement still opens; only its rows are missing");
		assertEquals(0, failed.getAiStatements());
	}

	@Test
	void theHistoryListsEveryStatementOfTheUserWithItsState() {
		connected();
		com.wealth.bss.common.model.entity.WealthStatementFigureEntity row = new com.wealth.bss.common.model.entity.WealthStatementFigureEntity();
		row.setId("11111111-1111-1111-1111-111111111111");
		row.setSource("bank_statement");
		row.setFileName("bank_statement.pdf");
		row.setOrigin("gmail");
		row.setStatus("opened");
		row.setFigures("{\"Closing Balance\":\"₹1.00\"}");
		com.wealth.bss.common.model.entity.WealthStatementFigureEntity lockedRow = new com.wealth.bss.common.model.entity.WealthStatementFigureEntity();
		lockedRow.setId("22222222-2222-2222-2222-222222222222");
		lockedRow.setSource("card");
		lockedRow.setOrigin("upload");
		lockedRow.setStatus("locked");
		lockedRow.setFigures("{}");
		when(statements.findHistory("user-1")).thenReturn(java.util.List.of(row, lockedRow));

		var history = service.statementHistory();

		assertEquals(2, history.size());
		assertEquals("opened", history.get(0).getStatus());
		assertEquals("₹1.00", history.get(0).getFigures().get("Closing Balance"));
		assertEquals("locked", history.get(1).getStatus());
		assertTrue(history.get(1).getFigures().isEmpty());
	}
}
