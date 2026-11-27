package com.wealth.bss.daoimplement;

import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.wealth.bss.common.model.dao.IfWealthInvestmentDao;
import com.wealth.bss.common.model.dto.PaginationDto;
import com.wealth.bss.common.model.entity.WealthAccountBalanceEntity;
import com.wealth.bss.common.model.entity.WealthHoldingDetailEntity;
import com.wealth.bss.common.model.entity.WealthHoldingEntity;
import com.wealth.bss.common.model.entity.WealthInstrumentEntity;
import com.wealth.bss.constants.WealthServiceConstants;
import com.wealth.bss.query.QueryConstants;
import com.wealth.common.logger.KarthiLogger;

@Repository
public class WealthInvestmentDaoImple extends WealthBaseDaoImple implements IfWealthInvestmentDao {

	private static final KarthiLogger LOGGER = new KarthiLogger(WealthInvestmentDaoImple.class);

	public WealthInvestmentDaoImple(JdbcTemplate jdbcTemplate) {
		super(jdbcTemplate);
	}

	@Override
	public List<WealthHoldingDetailEntity> findHoldingDetailsByUser(PaginationDto paginationDto) {
		String methodName = "findHoldingDetailsByUser";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		List<WealthHoldingDetailEntity> rows = jdbcTemplate.query(QueryConstants.FIND_HOLDING_DETAILS_BY_USER, new BeanPropertyRowMapper<>(WealthHoldingDetailEntity.class),
				paginationDto.getUserId());
		LOGGER.info(WealthServiceConstants.EXITING + methodName + " | rows=" + rows.size());
		return rows;
	}

	@Override
	public List<WealthAccountBalanceEntity> findHoldingValuesByUser(PaginationDto paginationDto) {
		String methodName = "findHoldingValuesByUser";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		List<WealthAccountBalanceEntity> rows = jdbcTemplate.query(QueryConstants.FIND_HOLDING_VALUES_BY_USER, new BeanPropertyRowMapper<>(WealthAccountBalanceEntity.class),
				paginationDto.getUserId());
		LOGGER.info(WealthServiceConstants.EXITING + methodName + " | rows=" + rows.size());
		return rows;
	}

	@Override
	public List<WealthAccountBalanceEntity> findHoldingAccountCashByUser(PaginationDto paginationDto) {
		String methodName = "findHoldingAccountCashByUser";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		List<WealthAccountBalanceEntity> rows = jdbcTemplate.query(QueryConstants.FIND_HOLDING_ACCOUNT_CASH_BY_USER, new BeanPropertyRowMapper<>(WealthAccountBalanceEntity.class),
				paginationDto.getUserId());
		LOGGER.info(WealthServiceConstants.EXITING + methodName + " | rows=" + rows.size());
		return rows;
	}

	@Override
	@Transactional
	public String saveHolding(String actor, String accountId, String priceSource, WealthHoldingEntity holding) {
		String methodName = "saveHolding";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		WealthHoldingEntity who = new WealthHoldingEntity();
		who.setActor(actor);
		setActor(who);
		String instrumentId = saveOne(accountId, priceSource, holding);
		LOGGER.info(WealthServiceConstants.EXITING + methodName);
		return instrumentId;
	}

	// The instrument, the holding and its price: the same three statements for a fetched holding and one entered by hand.
	private String saveOne(String accountId, String priceSource, WealthHoldingEntity holding) {
		WealthInstrumentEntity share = holding.getInstrument();
		String instrumentId = jdbcTemplate.queryForObject(QueryConstants.UPSERT_INSTRUMENT, String.class, share.getAssetClass(), share.getSymbol(), share.getName(),
				share.getCurrency(), share.getUnit(), share.getPriceSource(), share.getPriceSourceSymbol(), share.getIsin(), share.getExchange());
		jdbcTemplate.update(QueryConstants.UPSERT_HOLDING, accountId, instrumentId, holding.getQuantity(), holding.getInvestedValue(), holding.getAsOf(), holding.getProduct(), holding.getT1Quantity(),
				holding.getCollateralQuantity(), holding.getHaircut(), holding.getBrokerClose(), holding.getBrokerPnl(), holding.getBrokerPnlPercent());
		if (holding.getLastPrice() != null && holding.getLastPrice().signum() > 0) {
			jdbcTemplate.update(QueryConstants.INSERT_INSTRUMENT_PRICE, instrumentId, holding.getAsOf(), priceSource, holding.getLastPrice());
		}
		return instrumentId;
	}

	@Override
	@Transactional
	public int[] saveBrokerHoldings(String actor, String accountId, String priceSource, List<WealthHoldingEntity> holdings) {
		String methodName = "saveBrokerHoldings";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		WealthHoldingEntity who = new WealthHoldingEntity();
		who.setActor(actor);
		setActor(who);
		List<String> kept = new ArrayList<>();
		for (WealthHoldingEntity holding : holdings) {
			kept.add(saveOne(accountId, priceSource, holding));
		}
		int closed = jdbcTemplate.update(QueryConstants.CLOSE_MISSING_HOLDINGS, accountId, String.join(",", kept));
		LOGGER.info(WealthServiceConstants.EXITING + methodName + " | saved=" + kept.size() + " closed=" + closed);
		return new int[] { kept.size(), closed };
	}
}
