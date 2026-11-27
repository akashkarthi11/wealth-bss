package com.wealth.bss.serviceimplement;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.wealth.bss.common.model.dao.IfWealthAccountDao;
import com.wealth.bss.common.model.dao.IfWealthInvestmentDao;
import com.wealth.bss.common.model.dto.PaginationDto;
import com.wealth.bss.common.model.entity.WealthAccountBalanceEntity;
import com.wealth.bss.common.model.entity.WealthAccountEntity;
import com.wealth.bss.common.model.entity.WealthAccountSubtypeEntity;
import com.wealth.bss.common.model.entity.WealthHoldingDetailEntity;
import com.wealth.bss.common.model.entity.WealthHoldingEntity;
import com.wealth.bss.common.model.entity.WealthInstrumentEntity;
import com.wealth.bss.constants.WealthServiceConstants;
import com.wealth.bss.nexus.service.IfNexusReadService;
import com.wealth.bss.serviceinterface.IfInvestmentsService;
import com.wealth.bss.serviceinterface.IfRequestContextService;
import com.wealth.common.generic.BaseError;
import com.wealth.common.generic.KarthiException;
import com.wealth.common.assembler.GenericAssembler;
import com.wealth.common.logger.KarthiLogger;
import com.wealth.common.model.dto.CachedValueDto;
import com.wealth.common.model.dto.invest.HoldingDto;
import com.wealth.common.model.dto.invest.HoldingSaveRequestDto;
import com.wealth.common.model.dto.invest.InvestmentsDto;
import com.wealth.common.utils.generic.RequestHeaderUtil;

@Service
public class InvestmentsServiceImple implements IfInvestmentsService {

	private static final KarthiLogger LOGGER = new KarthiLogger(InvestmentsServiceImple.class);
	private static final BigDecimal HUNDRED = new BigDecimal("100");

	private final IfWealthInvestmentDao wealthInvestmentDao;
	private final IfNexusReadService nexusReadService;
	private final IfWealthAccountDao wealthAccountDao;
	private final IfRequestContextService requestContextService;

	public InvestmentsServiceImple(IfWealthInvestmentDao wealthInvestmentDao, IfNexusReadService nexusReadService, IfWealthAccountDao wealthAccountDao,
			IfRequestContextService requestContextService) {
		this.wealthInvestmentDao = wealthInvestmentDao;
		this.nexusReadService = nexusReadService;
		this.wealthAccountDao = wealthAccountDao;
		this.requestContextService = requestContextService;
	}

	// Not read from the tables on every call: the nexus returns it from its cache and refreshes it when it is older than the refresh time (rules 10).
	@Override
	public InvestmentsDto list() {
		return stamp(nexusReadService.read(WealthServiceConstants.NEXUS_READ_INVESTMENTS, InvestmentsDto.class, this::buildList));
	}

	@Override
	public InvestmentsDto refresh() {
		return stamp(nexusReadService.hardRefresh(WealthServiceConstants.NEXUS_READ_INVESTMENTS, InvestmentsDto.class, this::buildList));
	}

	// Only the owner's own investment accounts (valued by holdings), an asset class that fits the account's type, and a quantity and price that make sense.
	@Override
	public HoldingDto saveHolding(HoldingSaveRequestDto request) {
		String methodName = "saveHolding";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		requestContextService.requireAdmin();
		if (request == null || request.getAccountId() == null || !request.getAccountId().matches(WealthServiceConstants.UUID_PATTERN)) {
			throw new KarthiException(BaseError.INVALID_REQUEST);
		}
		PaginationDto paginationDto = new PaginationDto();
		paginationDto.setUserId(RequestHeaderUtil.getUserId());
		paginationDto.setId(request.getAccountId());
		WealthAccountEntity account = wealthAccountDao.findById(paginationDto);
		WealthAccountSubtypeEntity subtype = account == null ? null : wealthAccountDao.findAllSubtypes().stream().filter(s -> s.getCode().equals(account.getSubtypeCode())).findFirst().orElse(null);
		String assetClass = request.getAssetClass() == null ? "" : request.getAssetClass().trim();
		if (account == null || !WealthServiceConstants.ACCOUNT_ACTIVE.equals(account.getStatus()) || subtype == null || !WealthServiceConstants.VALUATION_HOLDINGS.equals(subtype.getValuation())
				|| !WealthServiceConstants.HOLDING_ASSET_CLASSES.getOrDefault(subtype.getCode(), java.util.List.of()).contains(assetClass)) {
			throw new KarthiException(BaseError.HOLDING_ACCOUNT_INVALID);
		}
		String name = request.getName() == null ? "" : request.getName().trim();
		String symbol = request.getSymbol() == null || request.getSymbol().isBlank() ? name.toUpperCase().replaceAll("[^A-Z0-9]+", "-").replaceAll("^-|-$", "") : request.getSymbol().trim().toUpperCase();
		String unit = request.getUnit() == null ? "" : request.getUnit().trim();
		long now = System.currentTimeMillis();
		java.sql.Timestamp asOf = request.getAsOf() == null ? new java.sql.Timestamp(now) : request.getAsOf();
		if (name.isEmpty() || name.length() > WealthServiceConstants.HOLDING_NAME_MAX_LENGTH || symbol.isEmpty() || symbol.length() > WealthServiceConstants.HOLDING_SYMBOL_MAX_LENGTH
				|| !WealthServiceConstants.HOLDING_UNITS.contains(unit) || request.getQuantity() == null || request.getQuantity().signum() <= 0
				|| request.getQuantity().doubleValue() > WealthServiceConstants.HOLDING_QUANTITY_MAX || (request.getInvestedValue() != null && request.getInvestedValue().signum() < 0)
				|| (request.getPrice() != null && request.getPrice().signum() <= 0) || asOf.getTime() > now) {
			throw new KarthiException(BaseError.HOLDING_INVALID);
		}
		WealthInstrumentEntity instrument = new WealthInstrumentEntity();
		instrument.setAssetClass(assetClass);
		instrument.setSymbol(symbol);
		instrument.setName(name);
		instrument.setCurrency(account.getCurrency());
		instrument.setUnit(unit);
		instrument.setPriceSource(WealthServiceConstants.PRICE_SOURCE_MANUAL);
		WealthHoldingEntity holding = new WealthHoldingEntity();
		holding.setInstrument(instrument);
		holding.setQuantity(request.getQuantity());
		holding.setInvestedValue(request.getInvestedValue());
		holding.setAsOf(asOf);
		holding.setLastPrice(request.getPrice());
		String instrumentId = wealthInvestmentDao.saveHolding(WealthServiceConstants.ACTOR_OWNER, account.getId(), WealthServiceConstants.PRICE_SOURCE_MANUAL, holding);
		nexusReadService.evict(WealthServiceConstants.NEXUS_READ_INVESTMENTS, paginationDto.getUserId());
		nexusReadService.evict(WealthServiceConstants.NEXUS_READ_ACCOUNTS, paginationDto.getUserId());
		paginationDto.setId(null);
		for (WealthHoldingDetailEntity row : wealthInvestmentDao.findHoldingDetailsByUser(paginationDto)) {
			if (account.getId().equals(row.getAccountId()) && instrumentId.equals(row.getInstrumentId())) {
				LOGGER.info(WealthServiceConstants.EXITING + methodName);
				return toHolding(row);
			}
		}
		LOGGER.info(WealthServiceConstants.EXITING + methodName);
		return new HoldingDto();
	}

	private InvestmentsDto stamp(CachedValueDto<InvestmentsDto> cached) {
		InvestmentsDto result = cached.getData();
		result.setFetchedAt(cached.getFetchedAt());
		result.setStale(cached.isStale());
		return result;
	}

	private InvestmentsDto buildList() {
		String methodName = "list";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		PaginationDto paginationDto = new PaginationDto();
		paginationDto.setUserId(RequestHeaderUtil.getUserId());
		BigDecimal invested = BigDecimal.ZERO;
		BigDecimal value = BigDecimal.ZERO;
		BigDecimal valueOfInvested = BigDecimal.ZERO;
		int unpriced = 0;
		List<HoldingDto> holdings = new ArrayList<>();
		for (WealthHoldingDetailEntity row : wealthInvestmentDao.findHoldingDetailsByUser(paginationDto)) {
			HoldingDto holding = toHolding(row);
			holdings.add(holding);
			if (holding.getCurrentValue() == null) {
				unpriced++;
			} else {
				boolean rupees = WealthServiceConstants.CURRENCY_INR.equals(row.getCurrency());
				if (rupees) {
					value = value.add(holding.getCurrentValue());
				} else if (row.getFxToInr() != null) {
					value = value.add(holding.getCurrentValue().multiply(row.getFxToInr()));
				}
				if (rupees && holding.getInvestedValue() != null) {
					invested = invested.add(holding.getInvestedValue());
					valueOfInvested = valueOfInvested.add(holding.getCurrentValue());
				}
			}
		}
		InvestmentsDto result = new InvestmentsDto();
		BigDecimal cash = null;
		java.sql.Timestamp cashAsOf = null;
		for (WealthAccountBalanceEntity row : wealthInvestmentDao.findHoldingAccountCashByUser(paginationDto)) {
			cash = (cash == null ? BigDecimal.ZERO : cash).add(row.getBalance());
			cashAsOf = cashAsOf == null || row.getAsOf().after(cashAsOf) ? row.getAsOf() : cashAsOf;
		}
		result.setCash(cash);
		result.setCashAsOf(cashAsOf);
		result.setHoldings(holdings);
		result.setHoldingCount(holdings.size());
		result.setUnpricedCount(unpriced);
		result.setInvested(invested);
		result.setCurrentValue(value.setScale(WealthServiceConstants.MONEY_SCALE, RoundingMode.HALF_UP));
		result.setGain(valueOfInvested.subtract(invested));
		result.setGainPercent(percent(result.getGain(), invested));
		LOGGER.info(WealthServiceConstants.EXITING + methodName + " | holdings=" + holdings.size());
		return result;
	}

	// Average price = invested value / quantity. Value = quantity x price, in the instrument's currency. The gain is shown only when the account and the instrument
	// use the same currency, so two currencies are never subtracted.
	private HoldingDto toHolding(WealthHoldingDetailEntity row) {
		HoldingDto dto = GenericAssembler.toDTO(row, HoldingDto.class);
		BigDecimal quantity = row.getQuantity();
		if (row.getInvestedValue() != null && quantity != null && quantity.signum() > 0) {
			dto.setAveragePrice(row.getInvestedValue().divide(quantity, WealthServiceConstants.PRICE_SCALE, RoundingMode.HALF_UP));
		}
		if (row.getPrice() != null && quantity != null) {
			dto.setCurrentValue(quantity.multiply(row.getPrice()).setScale(WealthServiceConstants.MONEY_SCALE, RoundingMode.HALF_UP));
			if (row.getInvestedValue() != null && row.getCurrency() != null && row.getCurrency().equals(row.getAccountCurrency())) {
				dto.setGain(dto.getCurrentValue().subtract(row.getInvestedValue()));
				dto.setGainPercent(percent(dto.getGain(), row.getInvestedValue()));
			}
		}
		return dto;
	}

	private BigDecimal percent(BigDecimal part, BigDecimal whole) {
		return whole.signum() > 0 ? part.multiply(HUNDRED).divide(whole, WealthServiceConstants.MONEY_SCALE, RoundingMode.HALF_UP) : null;
	}
}
