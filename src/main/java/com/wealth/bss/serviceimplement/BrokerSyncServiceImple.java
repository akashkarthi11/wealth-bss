package com.wealth.bss.serviceimplement;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Locale;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.wealth.bss.common.model.dao.IfWealthAccountBalanceDao;
import com.wealth.bss.common.model.dao.IfWealthAccountDao;
import com.wealth.bss.common.model.dao.IfWealthConnectionDao;
import com.wealth.bss.common.model.dao.IfWealthInvestmentDao;
import com.wealth.bss.common.model.dao.IfWealthUserConnectionDao;
import com.wealth.bss.common.model.entity.WealthUserConnectionEntity;
import com.wealth.bss.common.model.dto.PaginationDto;
import com.wealth.bss.common.model.entity.WealthAccountBalanceEntity;
import com.wealth.bss.common.model.entity.WealthAccountEntity;
import com.wealth.bss.common.model.entity.WealthConnectionEntity;
import com.wealth.bss.common.model.entity.WealthHoldingEntity;
import com.wealth.bss.common.model.entity.WealthInstrumentEntity;
import com.wealth.bss.config.AngelOneClient;
import com.wealth.bss.config.AngelOneClient.Credentials;
import com.wealth.bss.config.AngelOneClient.Holding;
import com.wealth.bss.config.SettingsCipher;
import com.wealth.bss.constants.WealthServiceConstants;
import com.wealth.bss.nexus.service.IfApiUsageService;
import com.wealth.bss.nexus.service.IfNexusCacheService;
import com.wealth.bss.nexus.service.IfNexusReadService;
import com.wealth.bss.serviceinterface.IfBrokerSyncService;
import com.wealth.bss.serviceinterface.IfRequestContextService;
import com.wealth.bss.serviceinterface.IfThirdPartyService;
import com.wealth.common.generic.BaseError;
import com.wealth.common.generic.KarthiException;
import com.wealth.common.logger.KarthiLogger;
import com.wealth.common.model.dto.admin.BrokerUserSaveRequestDto;
import com.wealth.common.model.dto.admin.BrokerUserStatusDto;
import com.wealth.common.model.dto.admin.ProviderRequestDto;
import com.wealth.common.model.dto.admin.ThirdPartyDto;
import com.wealth.common.utils.generic.RequestHeaderUtil;

@Service
public class BrokerSyncServiceImple implements IfBrokerSyncService {

	private static final KarthiLogger LOGGER = new KarthiLogger(BrokerSyncServiceImple.class);

	private final IfRequestContextService requestContextService;
	private final IfWealthConnectionDao connectionDao;
	private final IfWealthAccountDao accountDao;
	private final IfWealthInvestmentDao investmentDao;
	private final IfWealthAccountBalanceDao balanceDao;
	private final SettingsCipher cipher;
	private final AngelOneClient angelOne;
	private final IfThirdPartyService thirdPartyService;
	private final IfNexusCacheService nexusCache;
	private final IfNexusReadService nexusReadService;
	private final IfApiUsageService usage;
	private final IfWealthUserConnectionDao userDao;

	public BrokerSyncServiceImple(IfRequestContextService requestContextService, IfWealthConnectionDao connectionDao, IfWealthAccountDao accountDao,
			IfWealthInvestmentDao investmentDao, SettingsCipher cipher, AngelOneClient angelOne, IfThirdPartyService thirdPartyService,
			IfNexusCacheService nexusCache, IfApiUsageService usage, IfNexusReadService nexusReadService, IfWealthAccountBalanceDao balanceDao, IfWealthUserConnectionDao userDao) {
		this.userDao = userDao;
		this.balanceDao = balanceDao;
		this.nexusCache = nexusCache;
		this.nexusReadService = nexusReadService;
		this.usage = usage;
		this.requestContextService = requestContextService;
		this.connectionDao = connectionDao;
		this.accountDao = accountDao;
		this.investmentDao = investmentDao;
		this.cipher = cipher;
		this.angelOne = angelOne;
		this.thirdPartyService = thirdPartyService;
	}

	@Override
	public ThirdPartyDto fetchNow(ProviderRequestDto request) {
		String methodName = "fetchNow";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		requestContextService.requireAdmin();
		String provider = request == null ? null : request.getProvider();
		if (!WealthServiceConstants.BROKER_ANGEL_ONE.equals(provider)) {
			throw new KarthiException(BaseError.PROVIDER_INVALID);
		}
		WealthConnectionEntity row = connectionDao.findAll().stream().filter(r -> r.getProvider().equals(provider)).findFirst()
				.orElseThrow(() -> new KarthiException(BaseError.PROVIDER_NOT_FOUND));
		String owner = row.getOwnerUserId() == null ? RequestHeaderUtil.getUserId() : row.getOwnerUserId();
		try {
			String note = sync(row, owner, WealthServiceConstants.ACTOR_OWNER);
			LOGGER.info(WealthServiceConstants.EXITING + methodName + " | " + note);
			noted(row, note, WealthServiceConstants.ACTOR_OWNER);
			return thirdPartyService.list().stream().filter(dto -> provider.equals(dto.getProvider())).findFirst()
					.orElseThrow(() -> new KarthiException(BaseError.PROVIDER_NOT_FOUND));
		} catch (KarthiException e) {
			if (BaseError.BROKER_FETCH_FAILED.equals(e.getMessage())) {
				noted(row, "Failed: " + failureReason(e), WealthServiceConstants.ACTOR_OWNER);
			}
			throw e;
		}
	}

	// 18:30 IST on weekdays. A Redis lock keeps a second instance from running it as well (the internal lock when Redis is off or down; the run is safe to repeat).
	@Override
	@Scheduled(cron = WealthServiceConstants.BROKER_FETCH_CRON, zone = WealthServiceConstants.BROKER_ZONE)
	public void runDaily() {
		usage.runJob(WealthServiceConstants.USAGE_JOB_BROKER_FETCH, this::dailyRun);
	}

	// The daily fetch itself, counted as one run of the job "broker-fetch". True when it worked: every row was fetched, or another instance had the lock.
	private boolean dailyRun() {
		String methodName = "runDaily";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		if (!lock()) {
			LOGGER.info("Daily broker fetch skipped: another instance has it");
			return true;
		}
		WealthConnectionEntity shared = providerRow();
		if (shared != null && shared.getOwnerUserId() != null) {
			adoptShared(shared.getOwnerUserId());
		}
		boolean allWorked = dailyUsers();
		List<WealthConnectionEntity> rows = new ArrayList<>(connectionDao.findAll().stream().filter(r -> WealthServiceConstants.BROKER_ANGEL_ONE.equals(r.getProvider())
				&& r.isEnabled() && r.isAutoFetch() && r.getOwnerUserId() != null && r.getCredentialEnc() != null && userDao.find(r.getOwnerUserId(), WealthServiceConstants.BROKER_ANGEL_ONE) == null).toList());
		for (WealthConnectionEntity row : rows) {
			String note;
			try {
				note = sync(row, row.getOwnerUserId(), WealthServiceConstants.ACTOR_CRON);
			} catch (KarthiException e) {
				note = "Failed: " + failureReason(e);
			} catch (RuntimeException e) {
				LOGGER.warn("Daily broker fetch failed: " + e.getClass().getSimpleName());
				note = "Failed: the fetch could not be saved";
			}
			allWorked = allWorked && !note.startsWith("Failed");
			noted(row, note, WealthServiceConstants.ACTOR_CRON);
		}
		LOGGER.info(WealthServiceConstants.EXITING + methodName + " | rows=" + rows.size());
		return allWorked;
	}

	// Every user's own login with the daily fetch on, one at a time: a failure is noted on that user's connection only. True when all worked.
	private boolean dailyUsers() {
		WealthConnectionEntity provider = providerRow();
		if (provider == null || !provider.isEnabled() || provider.getBaseUrl() == null || provider.getBaseUrl().isBlank()) {
			return true;
		}
		boolean allWorked = true;
		for (WealthUserConnectionEntity row : userDao.findByProvider(WealthServiceConstants.BROKER_ANGEL_ONE)) {
			Map<String, String> values = angelOne.readExtra(cipher.decrypt(row.getCredentialEnc()));
			if (!"true".equals(values.get(KEY_AUTO))) {
				continue;
			}
			String note;
			try {
				note = syncWith(provider.getBaseUrl(), angelOne.credentials(values.get(KEY_API), values), row.getUserId(), WealthServiceConstants.ACTOR_CRON);
			} catch (KarthiException e) {
				note = "Failed: " + failureReason(e);
			} catch (RuntimeException e) {
				LOGGER.warn("Daily broker fetch failed for a user: " + e.getClass().getSimpleName());
				note = "Failed: the fetch could not be saved";
			}
			allWorked = allWorked && !note.startsWith("Failed");
			saveNote(row.getUserId(), note);
		}
		return allWorked;
	}

	// The nexus lock is in Redis while it answers, otherwise internal (rules 10a); either way a second run in the same window is refused.
	private boolean lock() {
		return nexusCache.tryLock(WealthServiceConstants.BROKER_LOCK, Duration.ofMinutes(WealthServiceConstants.BROKER_LOCK_MINUTES));
	}

	// Reads the holdings with the saved login and writes them to the owner's demat account; returns the note, e.g. "12 holdings, 1 closed".
	private String sync(WealthConnectionEntity row, String ownerUserId, String actor) {
		Credentials login = angelOne.credentials(cipher.decrypt(row.getCredentialEnc()), angelOne.readExtra(cipher.decrypt(row.getCredentialExtraEnc())));
		return syncWith(row.getBaseUrl(), login, ownerUserId, actor);
	}

	private String syncWith(String baseUrl, Credentials login, String ownerUserId, String actor) {
		if (!login.complete() || ownerUserId == null) {
			throw new KarthiException(BaseError.CREDENTIALS_INCOMPLETE);
		}
		List<Holding> fetched;
		BigDecimal cash;
		try {
			AngelOneClient.Portfolio portfolio = angelOne.portfolio(baseUrl, login);
			fetched = portfolio.holdings();
			cash = portfolio.cash();
		} catch (IllegalStateException e) {
			LOGGER.warn("Angel One fetch failed: " + e.getMessage());
			throw new KarthiException(BaseError.BROKER_FETCH_FAILED, e);
		}
		String accountId = demat(ownerUserId, actor);
		Timestamp now = new Timestamp(System.currentTimeMillis());
		List<WealthHoldingEntity> holdings = new ArrayList<>();
		for (Holding item : fetched) {
			if (item.quantity().signum() > 0) {
				holdings.add(holding(item, now));
			}
		}
		int[] result = investmentDao.saveBrokerHoldings(actor, accountId, WealthServiceConstants.BROKER_PRICE_SOURCE, holdings);
		saveCash(ownerUserId, accountId, cash, now, actor);
		nexusReadService.evict(WealthServiceConstants.NEXUS_READ_INVESTMENTS, ownerUserId);
		nexusReadService.evict(WealthServiceConstants.NEXUS_READ_ACCOUNTS, ownerUserId);
		return result[0] + (result[0] == 1 ? " holding" : " holdings") + ", " + result[1] + " closed";
	}

	// The cash in the trading account is kept as a reported balance of the demat account (an estimate: Angel One's figure may include margin). It is not the account's value
	// and not part of net worth; a failure here never fails the fetch.
	private void saveCash(String ownerUserId, String accountId, BigDecimal cash, Timestamp now, String actor) {
		if (cash == null) {
			return;
		}
		try {
			WealthAccountBalanceEntity entity = new WealthAccountBalanceEntity();
			entity.setActor(actor);
			entity.setUserId(ownerUserId);
			entity.setAccountId(accountId);
			entity.setAsOf(now);
			entity.setBalance(cash);
			entity.setStatus(WealthServiceConstants.BALANCE_REPORTED);
			entity.setNote(WealthServiceConstants.BROKER_CASH_NOTE);
			entity.setEnteredBy(WealthServiceConstants.BROKER_PRICE_SOURCE);
			balanceDao.insert(entity);
		} catch (RuntimeException e) {
			LOGGER.warn("The Angel One cash was not saved (" + e.getClass().getSimpleName() + ")");
		}
	}

	private static final String KEY_API = "apiKey";
	private static final String KEY_AUTO = "autoFetch";
	private static final String KEY_NOTE = "fetchNote";
	private static final String KEY_AT = "fetchAt";

	private WealthConnectionEntity providerRow() {
		return connectionDao.findAll().stream().filter(r -> WealthServiceConstants.BROKER_ANGEL_ONE.equals(r.getProvider())).findFirst().orElse(null);
	}

	private String currentUser() {
		String userId = RequestHeaderUtil.getUserId();
		if (userId == null || userId.isBlank()) {
			throw new KarthiException(BaseError.PROVIDER_INVALID);
		}
		return userId;
	}

	// A login saved as the shared one is moved once to its owner's own connection (apiKey and the other values in one encrypted block), then the shared copy is emptied. Never fails the caller.
	private void adoptShared(String userId) {
		try {
			WealthConnectionEntity shared = providerRow();
			if (shared == null || !userId.equals(shared.getOwnerUserId()) || shared.getCredentialEnc() == null || userDao.find(userId, WealthServiceConstants.BROKER_ANGEL_ONE) != null) {
				return;
			}
			Map<String, String> values = new LinkedHashMap<>(angelOne.readExtra(cipher.decrypt(shared.getCredentialExtraEnc())));
			String apiKey = cipher.decrypt(shared.getCredentialEnc());
			values.put(KEY_API, apiKey == null ? "" : apiKey);
			values.put(KEY_AUTO, String.valueOf(shared.isAutoFetch()));
			if (shared.getLastFetchNote() != null) {
				values.put(KEY_NOTE, shared.getLastFetchNote());
			}
			if (shared.getLastFetchAt() != null) {
				values.put(KEY_AT, String.valueOf(shared.getLastFetchAt().getTime()));
			}
			WealthUserConnectionEntity own = new WealthUserConnectionEntity();
			own.setUserId(userId);
			own.setProvider(WealthServiceConstants.BROKER_ANGEL_ONE);
			own.setCredentialEnc(cipher.encrypt(angelOne.writeExtra(values)));
			own.setKeyHint(shared.getKeyHint());
			own.setEnabled(true);
			if (userDao.upsert(own) != null) {
				connectionDao.clearCredential(WealthServiceConstants.BROKER_ANGEL_ONE);
			}
		} catch (RuntimeException e) {
			LOGGER.warn("The shared Angel One login could not be moved (" + e.getClass().getSimpleName() + ")");
		}
	}

	private Map<String, String> userValues(String userId) {
		adoptShared(userId);
		WealthUserConnectionEntity row = userDao.find(userId, WealthServiceConstants.BROKER_ANGEL_ONE);
		return row == null ? new LinkedHashMap<>() : new LinkedHashMap<>(angelOne.readExtra(cipher.decrypt(row.getCredentialEnc())));
	}

	private void writeValues(String userId, Map<String, String> values) {
		WealthUserConnectionEntity own = new WealthUserConnectionEntity();
		own.setUserId(userId);
		own.setProvider(WealthServiceConstants.BROKER_ANGEL_ONE);
		own.setCredentialEnc(cipher.encrypt(angelOne.writeExtra(values)));
		String key = values.getOrDefault(KEY_API, "");
		own.setKeyHint(key.length() > 4 ? key.substring(key.length() - 4) : null);
		own.setEnabled(true);
		userDao.upsert(own);
	}

	// The last fetch note and time, kept in the same encrypted block; a failure here never fails the fetch.
	private void saveNote(String userId, String note) {
		try {
			WealthUserConnectionEntity row = userDao.find(userId, WealthServiceConstants.BROKER_ANGEL_ONE);
			if (row == null) {
				return;
			}
			Map<String, String> values = new LinkedHashMap<>(angelOne.readExtra(cipher.decrypt(row.getCredentialEnc())));
			values.put(KEY_NOTE, note.length() > WealthServiceConstants.FETCH_NOTE_MAX_LENGTH ? note.substring(0, WealthServiceConstants.FETCH_NOTE_MAX_LENGTH) : note);
			values.put(KEY_AT, String.valueOf(System.currentTimeMillis()));
			userDao.saveCredential(userId, WealthServiceConstants.BROKER_ANGEL_ONE, cipher.encrypt(angelOne.writeExtra(values)));
		} catch (RuntimeException e) {
			LOGGER.warn("The Angel One fetch note was not kept (" + e.getClass().getSimpleName() + ")");
		}
	}

	private static boolean filled(String value) {
		return value != null && !value.isBlank();
	}

	@Override
	public BrokerUserStatusDto userStatus() {
		String userId = currentUser();
		Map<String, String> values = userValues(userId);
		WealthConnectionEntity provider = providerRow();
		WealthUserConnectionEntity row = userDao.find(userId, WealthServiceConstants.BROKER_ANGEL_ONE);
		BrokerUserStatusDto dto = new BrokerUserStatusDto();
		dto.setProviderOn(provider != null && provider.isEnabled() && filled(provider.getBaseUrl()));
		dto.setApiKeySet(filled(values.get(KEY_API)));
		dto.setClientCodeSet(filled(values.get("clientCode")));
		dto.setPinSet(filled(values.get("pin")));
		dto.setTotpSet(filled(values.get("totpSecret")));
		dto.setMacSet(filled(values.get("macAddress")));
		dto.setSaved(angelOne.credentials(values.get(KEY_API), values).complete());
		dto.setAutoFetch("true".equals(values.get(KEY_AUTO)));
		dto.setStatus(row == null ? null : row.getLastStatus());
		dto.setCheckedAt(row == null ? null : row.getLastCheckedAt());
		dto.setFetchNote(values.get(KEY_NOTE));
		try {
			dto.setFetchAt(filled(values.get(KEY_AT)) ? new Timestamp(Long.parseLong(values.get(KEY_AT))) : null);
		} catch (NumberFormatException e) {
			dto.setFetchAt(null);
		}
		return dto;
	}

	@Override
	public BrokerUserStatusDto userSave(BrokerUserSaveRequestDto request) {
		String userId = currentUser();
		if (request == null || providerRow() == null) {
			throw new KarthiException(request == null ? BaseError.PROVIDER_INVALID : BaseError.PROVIDER_NOT_FOUND);
		}
		Map<String, String> values = userValues(userId);
		Map<String, String> entered = new LinkedHashMap<>();
		entered.put(KEY_API, request.getApiKey());
		entered.put("clientCode", request.getClientCode());
		entered.put("pin", request.getPin());
		entered.put("totpSecret", request.getTotpSecret());
		entered.put("macAddress", request.getMacAddress());
		for (Map.Entry<String, String> item : entered.entrySet()) {
			String value = item.getValue() == null ? "" : item.getValue().trim();
			if (value.length() > WealthServiceConstants.BROKER_VALUE_MAX || value.chars().anyMatch(c -> c < ' ' || c == 127)) {
				throw new KarthiException(BaseError.PROVIDER_INVALID);
			}
			if (!value.isEmpty()) {
				values.put(item.getKey(), value);
			}
		}
		values.put(KEY_AUTO, String.valueOf(request.isAutoFetch()));
		writeValues(userId, values);
		return userStatus();
	}

	@Override
	public BrokerUserStatusDto userTest() {
		String userId = currentUser();
		Map<String, String> values = userValues(userId);
		Credentials login = angelOne.credentials(values.get(KEY_API), values);
		WealthConnectionEntity provider = providerRow();
		if (provider == null || !login.complete()) {
			throw new KarthiException(provider == null ? BaseError.PROVIDER_NOT_FOUND : BaseError.CREDENTIALS_INCOMPLETE);
		}
		boolean worked;
		try {
			angelOne.login(provider.getBaseUrl(), login);
			worked = true;
		} catch (IllegalStateException e) {
			LOGGER.warn("Angel One sign-in test failed: " + e.getMessage());
			worked = false;
		}
		userDao.saveCheck(userId, WealthServiceConstants.BROKER_ANGEL_ONE, worked ? "ok" : "failed");
		return userStatus();
	}

	@Override
	public BrokerUserStatusDto userFetch() {
		String userId = currentUser();
		Map<String, String> values = userValues(userId);
		WealthConnectionEntity provider = providerRow();
		if (provider == null || !provider.isEnabled() || !filled(provider.getBaseUrl())) {
			throw new KarthiException(BaseError.PROVIDER_NOT_FOUND);
		}
		try {
			saveNote(userId, syncWith(provider.getBaseUrl(), angelOne.credentials(values.get(KEY_API), values), userId, WealthServiceConstants.ACTOR_OWNER));
		} catch (KarthiException e) {
			if (BaseError.BROKER_FETCH_FAILED.equals(e.getMessage())) {
				saveNote(userId, "Failed: " + failureReason(e));
			}
			throw e;
		}
		return userStatus();
	}

	@Override
	public boolean userRemove() {
		return userDao.delete(currentUser(), WealthServiceConstants.BROKER_ANGEL_ONE);
	}

	// The owner's Angel One demat account, created on the first fetch.
	private String demat(String ownerUserId, String actor) {
		PaginationDto lookup = new PaginationDto();
		lookup.setUserId(ownerUserId);
		return accountDao.findByUser(lookup).stream().filter(a -> WealthServiceConstants.BROKER_ACCOUNT_KEY.equals(a.getAccountKey())).findFirst()
				.map(WealthAccountEntity::getId).orElseGet(() -> {
					WealthAccountEntity entity = new WealthAccountEntity();
					entity.setActor(actor);
					entity.setUserId(ownerUserId);
					entity.setAccountKey(WealthServiceConstants.BROKER_ACCOUNT_KEY);
					entity.setBankName(WealthServiceConstants.BROKER_BANK_NAME);
					entity.setAccountName(WealthServiceConstants.BROKER_ACCOUNT_NAME);
					entity.setSubtypeCode(WealthServiceConstants.BROKER_SUBTYPE);
					entity.setCurrency(WealthServiceConstants.CURRENCY_INR);
					WealthAccountEntity created = accountDao.insert(entity);
					return created == null ? accountDao.findByUser(lookup).stream().filter(a -> WealthServiceConstants.BROKER_ACCOUNT_KEY.equals(a.getAccountKey()))
							.findFirst().orElseThrow(() -> new KarthiException(BaseError.WEALTH_ACCOUNT_NOT_FOUND)).getId() : created.getId();
				});
	}

	// NSE and BSE symbols carry the old-app Yahoo form (LT.NS, LT.BO) so the price job can pick them up; an existing instrument is never changed.
	private WealthHoldingEntity holding(Holding item, Timestamp now) {
		String symbol = item.symbol().toUpperCase(Locale.ROOT);
		if (symbol.endsWith(WealthServiceConstants.BROKER_EQUITY_SUFFIX)) {
			symbol = symbol.substring(0, symbol.length() - WealthServiceConstants.BROKER_EQUITY_SUFFIX.length());
		}
		WealthInstrumentEntity share = new WealthInstrumentEntity();
		share.setAssetClass(WealthServiceConstants.INSTRUMENT_CLASS_INDIAN_EQUITY);
		share.setSymbol(symbol);
		share.setName(symbol);
		share.setCurrency(WealthServiceConstants.CURRENCY_INR);
		share.setUnit(WealthServiceConstants.INSTRUMENT_UNIT_SHARE);
		share.setPriceSource(WealthServiceConstants.PRICE_SOURCE_YAHOO);
		share.setPriceSourceSymbol(symbol + (WealthServiceConstants.EXCHANGE_BSE.equalsIgnoreCase(item.exchange()) ? WealthServiceConstants.YAHOO_SUFFIX_BSE : WealthServiceConstants.YAHOO_SUFFIX_NSE));
		share.setIsin(blankToNull(item.isin()));
		share.setExchange(blankToNull(item.exchange()));
		WealthHoldingEntity holding = new WealthHoldingEntity();
		holding.setInstrument(share);
		holding.setQuantity(item.quantity());
		holding.setInvestedValue(item.quantity().multiply(item.averagePrice()).max(BigDecimal.ZERO));
		holding.setAsOf(now);
		holding.setLastPrice(item.lastPrice());
		holding.setProduct(blankToNull(item.product()));
		holding.setT1Quantity(nonNegative(item.t1Quantity()));
		holding.setCollateralQuantity(nonNegative(item.collateralQuantity()));
		holding.setHaircut(item.haircut());
		holding.setBrokerClose(nonNegative(item.close()));
		holding.setBrokerPnl(item.profitAndLoss());
		holding.setBrokerPnlPercent(item.pnlPercent());
		return holding;
	}

	private static String blankToNull(String text) {
		return text == null || text.isBlank() ? null : text.trim();
	}

	// The table refuses a negative quantity or close, so a negative value from the broker is left empty.
	private static BigDecimal nonNegative(BigDecimal value) {
		return value == null || value.signum() < 0 ? null : value;
	}

	private WealthConnectionEntity noted(WealthConnectionEntity row, String note, String actor) {
		WealthConnectionEntity result = new WealthConnectionEntity();
		result.setActor(actor);
		result.setProvider(row.getProvider());
		result.setLastFetchNote(note.length() > WealthServiceConstants.FETCH_NOTE_MAX_LENGTH ? note.substring(0, WealthServiceConstants.FETCH_NOTE_MAX_LENGTH) : note);
		WealthConnectionEntity saved = connectionDao.saveFetchResult(result);
		return saved == null ? row : saved;
	}

	// The broker's own short reason when there is one (it never holds a secret); otherwise a fixed text for the error code.
	private static String failureReason(KarthiException e) {
		if (e.getCause() != null && e.getCause().getMessage() != null) {
			return e.getCause().getMessage();
		}
		return BaseError.CREDENTIALS_INCOMPLETE.equals(e.getMessage()) ? "the login details are incomplete" : "the fetch could not be done";
	}
}
