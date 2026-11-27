package com.wealth.bss.serviceimplement;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.wealth.bss.common.model.dao.IfWealthAccountDao;
import com.wealth.bss.common.model.dao.IfWealthConnectionDao;
import com.wealth.bss.common.model.dao.IfWealthInvestmentDao;
import com.wealth.bss.common.model.dto.PaginationDto;
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
	private final SettingsCipher cipher;
	private final AngelOneClient angelOne;
	private final IfThirdPartyService thirdPartyService;
	private final IfNexusCacheService nexusCache;
	private final IfNexusReadService nexusReadService;
	private final IfApiUsageService usage;

	public BrokerSyncServiceImple(IfRequestContextService requestContextService, IfWealthConnectionDao connectionDao, IfWealthAccountDao accountDao,
			IfWealthInvestmentDao investmentDao, SettingsCipher cipher, AngelOneClient angelOne, IfThirdPartyService thirdPartyService,
			IfNexusCacheService nexusCache, IfApiUsageService usage, IfNexusReadService nexusReadService) {
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
		List<WealthConnectionEntity> rows = new ArrayList<>(connectionDao.findAll().stream().filter(r -> WealthServiceConstants.BROKER_ANGEL_ONE.equals(r.getProvider())
				&& r.isEnabled() && r.isAutoFetch() && r.getOwnerUserId() != null).toList());
		boolean allWorked = true;
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

	// The nexus lock is in Redis while it answers, otherwise internal (rules 10a); either way a second run in the same window is refused.
	private boolean lock() {
		return nexusCache.tryLock(WealthServiceConstants.BROKER_LOCK, Duration.ofMinutes(WealthServiceConstants.BROKER_LOCK_MINUTES));
	}

	// Reads the holdings with the saved login and writes them to the owner's demat account; returns the note, e.g. "12 holdings, 1 closed".
	private String sync(WealthConnectionEntity row, String ownerUserId, String actor) {
		Credentials login = angelOne.credentials(cipher.decrypt(row.getCredentialEnc()), angelOne.readExtra(cipher.decrypt(row.getCredentialExtraEnc())));
		if (!login.complete() || ownerUserId == null) {
			throw new KarthiException(BaseError.CREDENTIALS_INCOMPLETE);
		}
		List<Holding> fetched;
		try {
			fetched = angelOne.holdings(row.getBaseUrl(), login);
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
		nexusReadService.evict(WealthServiceConstants.NEXUS_READ_INVESTMENTS, ownerUserId);
		nexusReadService.evict(WealthServiceConstants.NEXUS_READ_ACCOUNTS, ownerUserId);
		return result[0] + (result[0] == 1 ? " holding" : " holdings") + ", " + result[1] + " closed";
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
