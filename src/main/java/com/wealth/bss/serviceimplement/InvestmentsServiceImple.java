package com.wealth.bss.serviceimplement;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.wealth.bss.common.model.dao.IfWealthInvestmentDao;
import com.wealth.bss.common.model.dto.PaginationDto;
import com.wealth.bss.common.model.entity.WealthHoldingDetailEntity;
import com.wealth.bss.constants.WealthServiceConstants;
import com.wealth.bss.serviceinterface.IfInvestmentsService;
import com.wealth.common.assembler.GenericAssembler;
import com.wealth.common.logger.KarthiLogger;
import com.wealth.common.model.dto.invest.HoldingDto;
import com.wealth.common.model.dto.invest.InvestmentsDto;
import com.wealth.common.utils.generic.RequestHeaderUtil;

@Service
public class InvestmentsServiceImple implements IfInvestmentsService {

	private static final KarthiLogger LOGGER = new KarthiLogger(InvestmentsServiceImple.class);
	private static final BigDecimal HUNDRED = new BigDecimal("100");

	private final IfWealthInvestmentDao wealthInvestmentDao;

	public InvestmentsServiceImple(IfWealthInvestmentDao wealthInvestmentDao) {
		this.wealthInvestmentDao = wealthInvestmentDao;
	}

	@Override
	public InvestmentsDto list() {
		String methodName = "list";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		PaginationDto paginationDto = new PaginationDto();
		paginationDto.setUserId(RequestHeaderUtil.getUserId());
		BigDecimal invested = BigDecimal.ZERO;
		BigDecimal value = BigDecimal.ZERO;
		int unpriced = 0;
		List<HoldingDto> holdings = new ArrayList<>();
		for (WealthHoldingDetailEntity row : wealthInvestmentDao.findHoldingDetailsByUser(paginationDto)) {
			HoldingDto holding = toHolding(row);
			holdings.add(holding);
			if (holding.getCurrentValue() == null) {
				unpriced++;
			} else if (WealthServiceConstants.CURRENCY_INR.equals(row.getCurrency()) && holding.getInvestedValue() != null) {
				invested = invested.add(holding.getInvestedValue());
				value = value.add(holding.getCurrentValue());
			}
		}
		InvestmentsDto result = new InvestmentsDto();
		result.setHoldings(holdings);
		result.setHoldingCount(holdings.size());
		result.setUnpricedCount(unpriced);
		result.setInvested(invested);
		result.setCurrentValue(value);
		result.setGain(value.subtract(invested));
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
