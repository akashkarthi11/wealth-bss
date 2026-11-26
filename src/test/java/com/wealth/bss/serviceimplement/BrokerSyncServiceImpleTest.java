package com.wealth.bss.serviceimplement;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.wealth.bss.common.model.dao.IfWealthAccountDao;
import com.wealth.bss.common.model.dao.IfWealthConnectionDao;
import com.wealth.bss.common.model.dao.IfWealthInvestmentDao;
import com.wealth.bss.common.model.entity.WealthAccountEntity;
import com.wealth.bss.common.model.entity.WealthConnectionEntity;
import com.wealth.bss.common.model.entity.WealthHoldingEntity;
import com.wealth.bss.config.AngelOneClient;
import com.wealth.bss.config.AngelOneClient.Holding;
import com.wealth.bss.config.SettingsCipher;
import com.wealth.bss.serviceinterface.IfRequestContextService;
import com.wealth.common.generic.BaseError;
import com.wealth.common.generic.KarthiException;
import com.wealth.common.model.dto.admin.ProviderRequestDto;

class BrokerSyncServiceImpleTest {

	private static final String KEY = Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());
	private static final String OWNER = "11111111-1111-1111-1111-111111111111";

	private IfRequestContextService context;
	private IfWealthConnectionDao connections;
	private IfWealthAccountDao accounts;
	private IfWealthInvestmentDao investments;
	private SettingsCipher cipher;
	private AngelOneClient angel;
	private StringRedisTemplate redis;
	private ValueOperations<String, String> values;
	private ThirdPartyServiceImple thirdParty;
	private BrokerSyncServiceImple service;

	@SuppressWarnings("unchecked")
	@BeforeEach
	void setUp() {
		context = mock(IfRequestContextService.class);
		connections = mock(IfWealthConnectionDao.class);
		accounts = mock(IfWealthAccountDao.class);
		investments = mock(IfWealthInvestmentDao.class);
		cipher = new SettingsCipher(KEY);
		angel = spy(new AngelOneClient());
		redis = mock(StringRedisTemplate.class);
		values = mock(ValueOperations.class);
		thirdParty = mock(ThirdPartyServiceImple.class);
		when(redis.opsForValue()).thenReturn(values);
		when(values.setIfAbsent(any(), any(), any(java.time.Duration.class))).thenReturn(true);
		when(connections.saveFetchResult(any())).thenAnswer(i -> i.getArgument(0));
		when(investments.saveBrokerHoldings(any(), any(), any(), anyList())).thenReturn(new int[] { 2, 1 });
		service = new BrokerSyncServiceImple(context, connections, accounts, investments, cipher, angel, redis, thirdParty);
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
		verify(angel, never()).holdings(any(), any());
		verify(investments, never()).saveBrokerHoldings(any(), any(), any(), anyList());
	}

	@Test
	void theHoldingsAreWrittenToTheOwnersNewDematAccountWithSymbolsAndValuesMapped() {
		when(connections.findAll()).thenReturn(List.of(angelRow(true, true, false, OWNER)));
		org.mockito.Mockito.doReturn(List.of(holding("RELIANCE-EQ", "NSE", "10", "2450.55", "2512.3"), holding("tcs-eq", "BSE", "3", "3500", "0"),
				holding("SOLD-EQ", "NSE", "0", "100", "100"))).when(angel).holdings(any(), any());
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

		ArgumentCaptor<WealthConnectionEntity> note = ArgumentCaptor.forClass(WealthConnectionEntity.class);
		verify(connections).saveFetchResult(note.capture());
		assertEquals("2 holdings, 1 closed", note.getValue().getLastFetchNote());
		assertEquals("owner", note.getValue().getActor());
	}

	@Test
	void anExistingDematAccountIsReusedAndWithoutOwnerTheCallerIsUsed() {
		when(connections.findAll()).thenReturn(List.of(angelRow(true, true, false, null)));
		org.mockito.Mockito.doReturn(List.of(holding("LT-EQ", "NSE", "1", "1", "1"))).when(angel).holdings(any(), any());
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
		org.mockito.Mockito.doThrow(new IllegalStateException("Invalid totp")).when(angel).holdings(any(), any());

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
		org.mockito.Mockito.doReturn(List.of(holding("LT-EQ", "NSE", "1", "1", "1"))).when(angel).holdings(any(), any());
		when(accounts.findByUser(any())).thenReturn(List.of(account("acc-1")));

		service.runDaily();

		verify(investments, org.mockito.Mockito.times(1)).saveBrokerHoldings(eq("cron"), eq("acc-1"), any(), anyList());
		ArgumentCaptor<WealthConnectionEntity> note = ArgumentCaptor.forClass(WealthConnectionEntity.class);
		verify(connections).saveFetchResult(note.capture());
		assertEquals("cron", note.getValue().getActor());
	}

	@Test
	void theDailyRunSkipsWhenAnotherInstanceHoldsTheLockButGoesAheadWhenRedisIsDown() {
		when(connections.findAll()).thenReturn(List.of(angelRow(true, true, true, OWNER)));
		org.mockito.Mockito.doReturn(List.of()).when(angel).holdings(any(), any());
		when(accounts.findByUser(any())).thenReturn(List.of(account("acc-1")));

		when(values.setIfAbsent(any(), any(), any(java.time.Duration.class))).thenReturn(false);
		service.runDaily();
		verify(angel, never()).holdings(any(), any());

		when(values.setIfAbsent(any(), any(), any(java.time.Duration.class))).thenThrow(new IllegalStateException("redis down"));
		service.runDaily();
		verify(angel).holdings(any(), any());
	}

	@Test
	void aFailureInTheDailyRunIsNotedOnTheRowAndNeverThrown() {
		when(connections.findAll()).thenReturn(List.of(angelRow(true, true, true, OWNER), angelRow(false, true, true, OWNER)));
		org.mockito.Mockito.doThrow(new IllegalStateException("Account blocked")).when(angel).holdings(any(), any());

		service.runDaily();

		ArgumentCaptor<WealthConnectionEntity> notes = ArgumentCaptor.forClass(WealthConnectionEntity.class);
		verify(connections, org.mockito.Mockito.times(2)).saveFetchResult(notes.capture());
		assertEquals("Failed: Account blocked", notes.getAllValues().get(0).getLastFetchNote());
		assertTrue(notes.getAllValues().get(1).getLastFetchNote().startsWith("Failed: the login details are incomplete"));
	}
}
