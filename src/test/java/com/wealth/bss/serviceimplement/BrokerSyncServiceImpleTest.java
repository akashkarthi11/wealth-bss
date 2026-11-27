package com.wealth.bss.serviceimplement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.wealth.bss.common.model.dao.IfWealthAccountDao;
import com.wealth.bss.common.model.dao.IfWealthConnectionDao;
import com.wealth.bss.common.model.dao.IfWealthInvestmentDao;
import com.wealth.bss.common.model.entity.WealthAccountEntity;
import com.wealth.bss.common.model.entity.WealthConnectionEntity;
import com.wealth.bss.common.model.entity.WealthHoldingEntity;
import com.wealth.bss.config.AngelOneClient;
import com.wealth.bss.config.AngelOneClient.Holding;
import com.wealth.bss.nexus.service.IfNexusCacheService;
import com.wealth.bss.nexus.service.IfNexusReadService;
import com.wealth.bss.serviceinterface.IfThirdPartyService;
import com.wealth.bss.config.SettingsCipher;
import com.wealth.bss.serviceinterface.IfRequestContextService;
import com.wealth.common.generic.BaseError;
import com.wealth.common.generic.KarthiException;
import com.wealth.common.model.dto.admin.ProviderRequestDto;
import com.wealth.common.model.dto.admin.ThirdPartyDto;

class BrokerSyncServiceImpleTest {

	private static final String KEY = Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());
	private static final String OWNER = "11111111-1111-1111-1111-111111111111";

	private IfRequestContextService context;
	private IfWealthConnectionDao connections;
	private IfWealthAccountDao accounts;
	private IfWealthInvestmentDao investments;
	private SettingsCipher cipher;
	private AngelOneClient angel;
	private IfThirdPartyService thirdParty;
	private IfNexusCacheService nexusCache;
	private IfNexusReadService nexusRead;
	private com.wealth.bss.nexus.service.IfApiUsageService usage;
	private com.wealth.bss.common.model.dao.IfWealthAccountBalanceDao balances;
	private BrokerSyncServiceImple service;
	private com.wealth.bss.common.model.dao.IfWealthUserConnectionDao users;
	private final java.util.Map<String, com.wealth.bss.common.model.entity.WealthUserConnectionEntity> store = new java.util.HashMap<>();

	@BeforeEach
	void setUp() {
		context = mock(IfRequestContextService.class);
		connections = mock(IfWealthConnectionDao.class);
		accounts = mock(IfWealthAccountDao.class);
		investments = mock(IfWealthInvestmentDao.class);
		cipher = new SettingsCipher(KEY);
		angel = spy(new AngelOneClient());
		thirdParty = mock(IfThirdPartyService.class);
		nexusCache = mock(IfNexusCacheService.class);
		nexusRead = mock(IfNexusReadService.class);
		ThirdPartyDto row = new ThirdPartyDto();
		row.setProvider("angel-one");
		when(thirdParty.list()).thenReturn(List.of(row));
		when(nexusCache.tryLock(any(), any(java.time.Duration.class))).thenReturn(true);
		when(connections.saveFetchResult(any())).thenAnswer(i -> i.getArgument(0));
		when(investments.saveBrokerHoldings(any(), any(), any(), anyList())).thenReturn(new int[] { 2, 1 });
		usage = mock(com.wealth.bss.nexus.service.IfApiUsageService.class);
		org.mockito.Mockito.doAnswer(i -> {
			((java.util.function.BooleanSupplier) i.getArgument(1)).getAsBoolean();
			return null;
		}).when(usage).runJob(any(), any());
		balances = mock(com.wealth.bss.common.model.dao.IfWealthAccountBalanceDao.class);
		users = mock(com.wealth.bss.common.model.dao.IfWealthUserConnectionDao.class);
		store.clear();
		when(users.find(any(), any())).thenAnswer(i -> store.get(i.getArgument(0) + "|" + i.getArgument(1)));
		when(users.upsert(any())).thenAnswer(i -> {
			com.wealth.bss.common.model.entity.WealthUserConnectionEntity e = i.getArgument(0);
			store.put(e.getUserId() + "|" + e.getProvider(), e);
			return e;
		});
		when(users.saveCredential(any(), any(), any())).thenAnswer(i -> {
			com.wealth.bss.common.model.entity.WealthUserConnectionEntity e = store.get(i.getArgument(0) + "|" + i.getArgument(1));
			if (e != null) {
				e.setCredentialEnc(i.getArgument(2));
			}
			return e;
		});
		when(users.findByProvider(any())).thenAnswer(i -> new java.util.ArrayList<>(store.values()));
		when(users.delete(any(), any())).thenAnswer(i -> store.remove(i.getArgument(0) + "|" + i.getArgument(1)) != null);
		com.wealth.common.utils.generic.RequestHeaderUtil.setUserId(OWNER);
		service = new BrokerSyncServiceImple(context, connections, accounts, investments, cipher, angel, thirdParty, nexusCache, usage, nexusRead, balances, users);
	}

	private WealthConnectionEntity angelRow(boolean complete, boolean enabled, boolean autoFetch, String owner) {
		WealthConnectionEntity row = new WealthConnectionEntity();
		row.setProvider("angel-one");
		row.setBaseUrl("https://apiconnect.angelone.in");
		row.setCredentialEnc(cipher.encrypt("api-key"));
		row.setCredentialExtraEnc(cipher.encrypt(complete ? "{\"clientCode\":\"A1\",\"pin\":\"1234\",\"totpSecret\":\"JBSWY3DP\",\"macAddress\":\"00:11\"}" : "{\"clientCode\":\"A1\"}"));
		row.setEnabled(enabled);
		row.setAutoFetch(autoFetch);
		row.setOwnerUserId(owner);
		return row;
	}

	private ProviderRequestDto request(String provider) {
		ProviderRequestDto r = new ProviderRequestDto();
		r.setProvider(provider);
		return r;
	}

	private String codeOf(Runnable call) {
		return assertThrows(KarthiException.class, call::run).getMessage();
	}

	private WealthAccountEntity account(String id) {
		WealthAccountEntity a = new WealthAccountEntity();
		a.setId(id);
		a.setAccountKey("angel-one-demat");
		return a;
	}

	private Holding holding(String symbol, String exchange, String quantity, String average, String last) {
		return new Holding(symbol, "INE" + symbol, exchange, "1", new BigDecimal(quantity), new BigDecimal(average), new BigDecimal(last), "DELIVERY", new BigDecimal("2"), BigDecimal.ZERO,
				new BigDecimal("0.2"), new BigDecimal("2400"), new BigDecimal("-5"), new BigDecimal("-0.2"));
	}

	private com.wealth.common.model.dto.admin.BrokerUserSaveRequestDto login(String apiKey, String code, String pin, String totp, String mac, boolean auto) {
		com.wealth.common.model.dto.admin.BrokerUserSaveRequestDto r = new com.wealth.common.model.dto.admin.BrokerUserSaveRequestDto();
		r.setApiKey(apiKey);
		r.setClientCode(code);
		r.setPin(pin);
		r.setTotpSecret(totp);
		r.setMacAddress(mac);
		r.setAutoFetch(auto);
		return r;
	}

	@Test
	void aUserSavesTheirOwnLoginEncryptedAndTheStatusOnlyShowsWhichValuesAreSaved() {
		when(connections.findAll()).thenReturn(List.of(angelRow(false, true, false, null)));
		var status = service.userSave(login("my-api-key", "A1", "", "", "", true));
		assertFalse(status.isSaved(), "the login is not complete yet");
		assertTrue(status.isApiKeySet() && status.isClientCodeSet() && !status.isPinSet() && !status.isTotpSet() && status.isAutoFetch() && status.isProviderOn());

		status = service.userSave(login("", "", "1234", "JBSWY3DP", "00:11", true));
		assertTrue(status.isSaved() && status.isApiKeySet() && status.isMacSet(), "a blank value keeps the saved one");
		String stored = store.get(OWNER + "|angel-one").getCredentialEnc();
		assertFalse(stored.contains("my-api-key") || stored.contains("1234"), "the saved block is encrypted");
		assertTrue(cipher.decrypt(stored).contains("my-api-key"));
		assertEquals("-key", store.get(OWNER + "|angel-one").getKeyHint());

		assertEquals(BaseError.PROVIDER_INVALID, codeOf(() -> service.userSave(login("x".repeat(201), "", "", "", "", false))));
		assertEquals(BaseError.PROVIDER_INVALID, codeOf(() -> service.userSave(login("a\nb", "", "", "", "", false))));
		assertEquals(BaseError.PROVIDER_INVALID, codeOf(() -> service.userSave(null)));
		when(connections.findAll()).thenReturn(List.of());
		assertEquals(BaseError.PROVIDER_NOT_FOUND, codeOf(() -> service.userSave(login("k", "", "", "", "", false))));
	}

	@Test
	void theSharedLoginIsMovedToItsOwnerOnFirstUseAndThenEmptied() {
		WealthConnectionEntity shared = angelRow(true, true, true, OWNER);
		shared.setLastFetchNote("2 holdings, 0 closed");
		when(connections.findAll()).thenReturn(List.of(shared));

		var status = service.userStatus();

		assertTrue(status.isSaved() && status.isAutoFetch() && status.isProviderOn());
		assertEquals("2 holdings, 0 closed", status.getFetchNote());
		verify(connections).clearCredential("angel-one");
		assertTrue(cipher.decrypt(store.get(OWNER + "|angel-one").getCredentialEnc()).contains("api-key"));

		com.wealth.common.utils.generic.RequestHeaderUtil.setUserId("22222222-2222-2222-2222-222222222222");
		assertFalse(service.userStatus().isSaved(), "another user does not get the owner's login");
		com.wealth.common.utils.generic.RequestHeaderUtil.setUserId(OWNER);
	}

	@Test
	void aUserFetchWritesTheirOwnHoldingsKeepsTheNoteAndRefusesAnIncompleteLogin() {
		when(connections.findAll()).thenReturn(List.of(angelRow(false, true, false, null)));
		service.userSave(login("k", "A1", "", "", "", false));
		assertEquals(BaseError.CREDENTIALS_INCOMPLETE, codeOf(() -> service.userFetch()));
		verify(angel, never()).portfolio(any(), any());

		service.userSave(login("", "", "1234", "JBSWY3DP", "00:11", false));
		org.mockito.Mockito.doReturn(new AngelOneClient.Portfolio(List.of(holding("LT-EQ", "NSE", "1", "1", "1")), null)).when(angel).portfolio(any(), any());
		when(accounts.findByUser(any())).thenReturn(List.of(account("demat-1")));

		var status = service.userFetch();

		assertEquals("2 holdings, 1 closed", status.getFetchNote());
		assertTrue(status.getFetchAt() != null);
		verify(investments).saveBrokerHoldings(eq("owner"), eq("demat-1"), any(), anyList());
		verify(nexusRead, org.mockito.Mockito.atLeastOnce()).evict(any(), eq(OWNER));

		org.mockito.Mockito.doThrow(new IllegalStateException("Angel One could not be reached")).when(angel).portfolio(any(), any());
		assertEquals(BaseError.BROKER_FETCH_FAILED, codeOf(() -> service.userFetch()));
		assertTrue(service.userStatus().getFetchNote().startsWith("Failed"));
		assertTrue(service.userRemove());
		assertFalse(service.userRemove());
	}

	@Test
	void theDailyRunFetchesEveryUserWhoSwitchedItOnAndNotedTheFailureOnThatUserOnly() {
		when(connections.findAll()).thenReturn(List.of(angelRow(false, true, false, null)));
		com.wealth.common.utils.generic.RequestHeaderUtil.setUserId("22222222-2222-2222-2222-222222222222");
		service.userSave(login("k2", "B2", "1234", "JBSWY3DP", "00:11", true));
		com.wealth.common.utils.generic.RequestHeaderUtil.setUserId(OWNER);
		service.userSave(login("k1", "A1", "1234", "JBSWY3DP", "00:11", false));
		com.wealth.common.utils.generic.RequestHeaderUtil.setUserId("33333333-3333-3333-3333-333333333333");
		service.userSave(login("k3", "C3", "1234", "JBSWY3DP", "00:11", true));
		com.wealth.common.utils.generic.RequestHeaderUtil.setUserId(OWNER);
		org.mockito.Mockito.doReturn(new AngelOneClient.Portfolio(List.of(holding("LT-EQ", "NSE", "1", "1", "1")), null)).doThrow(new IllegalStateException("Angel One could not be reached"))
				.when(angel).portfolio(any(), any());
		when(accounts.findByUser(any())).thenReturn(List.of(account("demat-1")));

		service.runDaily();

		verify(angel, org.mockito.Mockito.times(2)).portfolio(any(), any());
		String noteOne = cipher.decrypt(store.get("22222222-2222-2222-2222-222222222222|angel-one").getCredentialEnc());
		String noteTwo = cipher.decrypt(store.get("33333333-3333-3333-3333-333333333333|angel-one").getCredentialEnc());
		assertTrue((noteOne.contains("holdings") && noteTwo.contains("Failed")) || (noteOne.contains("Failed") && noteTwo.contains("holdings")), "one user's failure does not stop the next");
		assertFalse(cipher.decrypt(store.get(OWNER + "|angel-one").getCredentialEnc()).contains("fetchNote"), "a user with the daily fetch off is not fetched");
	}

	@Test
	void fetchingIsForAnAdminAndOnlyForAngelOne() {
		doThrow(new KarthiException(BaseError.ADMIN_ONLY_ACTION)).when(context).requireAdmin();
		assertEquals(BaseError.ADMIN_ONLY_ACTION, codeOf(() -> service.fetchNow(request("angel-one"))));
		org.mockito.Mockito.reset(context);
		assertEquals(BaseError.PROVIDER_INVALID, codeOf(() -> service.fetchNow(request("gemini"))));
		assertEquals(BaseError.PROVIDER_INVALID, codeOf(() -> service.fetchNow(null)));
		when(connections.findAll()).thenReturn(List.of());
		assertEquals(BaseError.PROVIDER_NOT_FOUND, codeOf(() -> service.fetchNow(request("angel-one"))));
		verify(investments, never()).saveBrokerHoldings(any(), any(), any(), anyList());
	}

	@Test
	void anIncompleteLoginIsRefusedBeforeAnyCallToTheBroker() {
		when(connections.findAll()).thenReturn(List.of(angelRow(false, true, false, OWNER)));
		assertEquals(BaseError.CREDENTIALS_INCOMPLETE, codeOf(() -> service.fetchNow(request("angel-one"))));
		verify(angel, never()).portfolio(any(), any());
		verify(investments, never()).saveBrokerHoldings(any(), any(), any(), anyList());
	}

	@Test
	void theHoldingsAreWrittenToTheOwnersNewDematAccountWithSymbolsAndValuesMapped() {
		when(connections.findAll()).thenReturn(List.of(angelRow(true, true, false, OWNER)));
		org.mockito.Mockito.doReturn(new AngelOneClient.Portfolio(List.of(holding("RELIANCE-EQ", "NSE", "10", "2450.55", "2512.3"), holding("tcs-eq", "BSE", "3", "3500", "0"),
				holding("SOLD-EQ", "NSE", "0", "100", "100")), null)).when(angel).portfolio(any(), any());
		when(accounts.findByUser(any())).thenReturn(List.of());
		when(accounts.insert(any())).thenReturn(account("acc-1"));

		service.fetchNow(request("angel-one"));

		ArgumentCaptor<WealthAccountEntity> created = ArgumentCaptor.forClass(WealthAccountEntity.class);
		verify(accounts).insert(created.capture());
		assertEquals(OWNER, created.getValue().getUserId());
		assertEquals("demat_equity", created.getValue().getSubtypeCode());
		assertEquals("Angel One", created.getValue().getBankName());
		assertEquals("INR", created.getValue().getCurrency());

		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<WealthHoldingEntity>> saved = ArgumentCaptor.forClass(List.class);
		verify(investments).saveBrokerHoldings(eq("owner"), eq("acc-1"), eq("angel-one"), saved.capture());
		List<WealthHoldingEntity> rows = saved.getValue();
		assertEquals(2, rows.size(), "a share with quantity 0 is not a holding");
		assertEquals("RELIANCE", rows.get(0).getInstrument().getSymbol());
		assertEquals("RELIANCE.NS", rows.get(0).getInstrument().getPriceSourceSymbol());
		assertEquals("indian_equity", rows.get(0).getInstrument().getAssetClass());
		assertEquals("share", rows.get(0).getInstrument().getUnit());
		assertEquals(0, new BigDecimal("24505.50").compareTo(rows.get(0).getInvestedValue()));
		assertEquals(0, new BigDecimal("2512.3").compareTo(rows.get(0).getLastPrice()));
		assertEquals("INERELIANCE-EQ", rows.get(0).getInstrument().getIsin());
		assertEquals("NSE", rows.get(0).getInstrument().getExchange());
		assertEquals("DELIVERY", rows.get(0).getProduct());
		assertEquals(0, new BigDecimal("2").compareTo(rows.get(0).getT1Quantity()));
		assertEquals(0, new BigDecimal("2400").compareTo(rows.get(0).getBrokerClose()));
		assertEquals(0, new BigDecimal("-5").compareTo(rows.get(0).getBrokerPnl()));
		assertEquals(0, new BigDecimal("-0.2").compareTo(rows.get(0).getBrokerPnlPercent()));
		assertEquals("TCS", rows.get(1).getInstrument().getSymbol());
		assertEquals("TCS.BO", rows.get(1).getInstrument().getPriceSourceSymbol());

		verify(nexusRead).evict("investments", OWNER);
		verify(nexusRead).evict("accounts-overview", OWNER);
		ArgumentCaptor<WealthConnectionEntity> note = ArgumentCaptor.forClass(WealthConnectionEntity.class);
		verify(connections).saveFetchResult(note.capture());
		assertEquals("2 holdings, 1 closed", note.getValue().getLastFetchNote());
		assertEquals("owner", note.getValue().getActor());
	}

	@Test
	void anExistingDematAccountIsReusedAndWithoutOwnerTheCallerIsUsed() {
		when(connections.findAll()).thenReturn(List.of(angelRow(true, true, false, null)));
		org.mockito.Mockito.doReturn(new AngelOneClient.Portfolio(List.of(holding("LT-EQ", "NSE", "1", "1", "1")), null)).when(angel).portfolio(any(), any());
		when(accounts.findByUser(any())).thenReturn(List.of(account("acc-9")));
		try (var header = org.mockito.Mockito.mockStatic(com.wealth.common.utils.generic.RequestHeaderUtil.class)) {
			header.when(com.wealth.common.utils.generic.RequestHeaderUtil::getUserId).thenReturn(OWNER);
			service.fetchNow(request("angel-one"));
		}
		verify(accounts, never()).insert(any());
		verify(investments).saveBrokerHoldings(eq("owner"), eq("acc-9"), any(), anyList());
	}

	@Test
	void whenTheBrokerRefusesTheFailureIsNotedWithItsShortReasonAndNothingIsWritten() {
		when(connections.findAll()).thenReturn(List.of(angelRow(true, true, false, OWNER)));
		org.mockito.Mockito.doThrow(new IllegalStateException("Invalid totp")).when(angel).portfolio(any(), any());

		assertEquals(BaseError.BROKER_FETCH_FAILED, codeOf(() -> service.fetchNow(request("angel-one"))));

		ArgumentCaptor<WealthConnectionEntity> note = ArgumentCaptor.forClass(WealthConnectionEntity.class);
		verify(connections).saveFetchResult(note.capture());
		assertEquals("Failed: Invalid totp", note.getValue().getLastFetchNote());
		verify(investments, never()).saveBrokerHoldings(any(), any(), any(), anyList());
		verify(accounts, never()).insert(any());
	}

	@Test
	void theDailyRunFetchesOnlyEnabledRowsWithTheSwitchOnAndAnOwnerAsCron() {
		when(connections.findAll()).thenReturn(List.of(angelRow(true, true, true, OWNER), angelRow(true, false, true, OWNER), angelRow(true, true, false, OWNER),
				angelRow(true, true, true, null)));
		org.mockito.Mockito.doReturn(new AngelOneClient.Portfolio(List.of(holding("LT-EQ", "NSE", "1", "1", "1")), null)).when(angel).portfolio(any(), any());
		when(accounts.findByUser(any())).thenReturn(List.of(account("acc-1")));

		service.runDaily();

		verify(investments, org.mockito.Mockito.times(1)).saveBrokerHoldings(eq("cron"), eq("acc-1"), any(), anyList());
		verify(connections).clearCredential("angel-one");
		assertTrue(cipher.decrypt(store.get(OWNER + "|angel-one").getCredentialEnc()).contains("fetchNote"), "the owner's login was moved to their own connection and the note kept there");
		verify(connections, never()).saveFetchResult(any());
	}

	@Test
	void theDailyRunSkipsWhenTheNexusLockIsHeldAndGoesAheadWhenItIsFree() {
		when(connections.findAll()).thenReturn(List.of(angelRow(true, true, true, OWNER)));
		org.mockito.Mockito.doReturn(new AngelOneClient.Portfolio(List.of(), null)).when(angel).portfolio(any(), any());
		when(accounts.findByUser(any())).thenReturn(List.of(account("acc-1")));

		when(nexusCache.tryLock(eq("wealth:broker-fetch:lock"), any(java.time.Duration.class))).thenReturn(false);
		service.runDaily();
		verify(angel, never()).portfolio(any(), any());

		when(nexusCache.tryLock(eq("wealth:broker-fetch:lock"), any(java.time.Duration.class))).thenReturn(true);
		service.runDaily();
		verify(angel).portfolio(any(), any());
	}

	@Test
	void theDailyRunIsCountedAsOneRunOfTheJobAndItsResultIsHonest() {
		when(connections.findAll()).thenReturn(List.of(angelRow(true, true, true, OWNER)));
		org.mockito.Mockito.doReturn(new AngelOneClient.Portfolio(List.of(), null)).when(angel).portfolio(any(), any());
		when(accounts.findByUser(any())).thenReturn(List.of(account("acc-1")));
		ArgumentCaptor<java.util.function.BooleanSupplier> work = ArgumentCaptor.forClass(java.util.function.BooleanSupplier.class);

		service.runDaily();
		verify(usage).runJob(eq("broker-fetch"), work.capture());
		assertTrue(work.getValue().getAsBoolean(), "a run where every row worked is a good run");

		org.mockito.Mockito.doThrow(new IllegalStateException("Account blocked")).when(angel).portfolio(any(), any());
		assertEquals(false, work.getValue().getAsBoolean(), "a run with a failed row is a failed run");

		when(nexusCache.tryLock(eq("wealth:broker-fetch:lock"), any(java.time.Duration.class))).thenReturn(false);
		assertTrue(work.getValue().getAsBoolean(), "another instance holding the lock is not a failure");
	}

	@Test
	void aFailureInTheDailyRunIsNotedOnTheRowAndNeverThrown() {
		when(connections.findAll()).thenReturn(List.of(angelRow(true, true, true, OWNER)));
		org.mockito.Mockito.doThrow(new IllegalStateException("Account blocked")).when(angel).portfolio(any(), any());

		service.runDaily();

		assertTrue(cipher.decrypt(store.get(OWNER + "|angel-one").getCredentialEnc()).contains("Failed: Account blocked"), "the failure is noted on the user's own connection");
	}

	@Test
	void theCashInTheTradingAccountIsKeptAsAReportedBalanceOfTheDematAccountAndNeverFailsTheFetch() {
		when(connections.findAll()).thenReturn(List.of(angelRow(true, true, false, null)));
		org.mockito.Mockito.doReturn(new AngelOneClient.Portfolio(List.of(holding("LT-EQ", "NSE", "1", "1", "1")), new java.math.BigDecimal("1250.75"))).when(angel).portfolio(any(), any());
		when(accounts.findByUser(any())).thenReturn(List.of(account("acc-9")));
		try (var header = org.mockito.Mockito.mockStatic(com.wealth.common.utils.generic.RequestHeaderUtil.class)) {
			header.when(com.wealth.common.utils.generic.RequestHeaderUtil::getUserId).thenReturn(OWNER);
			service.fetchNow(request("angel-one"));
		}
		verify(balances).insert(org.mockito.ArgumentMatchers.argThat(e -> "acc-9".equals(e.getAccountId()) && new java.math.BigDecimal("1250.75").equals(e.getBalance()) && "reported".equals(e.getStatus())
				&& OWNER.equals(e.getUserId()) && e.getNote().contains("may include margin")));

		org.mockito.Mockito.doThrow(new IllegalStateException("db down")).when(balances).insert(any());
		try (var header = org.mockito.Mockito.mockStatic(com.wealth.common.utils.generic.RequestHeaderUtil.class)) {
			header.when(com.wealth.common.utils.generic.RequestHeaderUtil::getUserId).thenReturn(OWNER);
			service.fetchNow(request("angel-one"));
		}
		verify(investments, org.mockito.Mockito.times(2)).saveBrokerHoldings(any(), eq("acc-9"), any(), anyList());
	}

	@Test
	void noCashFromTheBrokerMeansNoBalanceIsWritten() {
		when(connections.findAll()).thenReturn(List.of(angelRow(true, true, false, null)));
		org.mockito.Mockito.doReturn(new AngelOneClient.Portfolio(List.of(holding("LT-EQ", "NSE", "1", "1", "1")), null)).when(angel).portfolio(any(), any());
		when(accounts.findByUser(any())).thenReturn(List.of(account("acc-9")));
		try (var header = org.mockito.Mockito.mockStatic(com.wealth.common.utils.generic.RequestHeaderUtil.class)) {
			header.when(com.wealth.common.utils.generic.RequestHeaderUtil::getUserId).thenReturn(OWNER);
			service.fetchNow(request("angel-one"));
		}
		verify(balances, never()).insert(any());
	}
}
