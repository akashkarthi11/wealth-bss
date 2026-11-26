package com.wealth.bss.daoimplement;

import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.wealth.bss.common.model.dao.IfWealthInvestmentDao;
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
	@Transactional
	public int[] saveBrokerHoldings(String actor, String accountId, String priceSource, List<WealthHoldingEntity> holdings) {
		String methodName = "saveBrokerHoldings";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		WealthHoldingEntity who = new WealthHoldingEntity();
		who.setActor(actor);
		setActor(who);
		List<String> kept = new ArrayList<>();
		for (WealthHoldingEntity holding : holdings) {
			WealthInstrumentEntity share = holding.getInstrument();
			String instrumentId = jdbcTemplate.queryForObject(QueryConstants.UPSERT_INSTRUMENT, String.class, share.getAssetClass(), share.getSymbol(), share.getName(),
					share.getCurrency(), share.getUnit(), share.getPriceSource(), share.getPriceSourceSymbol());
			jdbcTemplate.update(QueryConstants.UPSERT_HOLDING, accountId, instrumentId, holding.getQuantity(), holding.getInvestedValue(), holding.getAsOf());
			if (holding.getLastPrice() != null && holding.getLastPrice().signum() > 0) {
				jdbcTemplate.update(QueryConstants.INSERT_INSTRUMENT_PRICE, instrumentId, holding.getAsOf(), priceSource, holding.getLastPrice());
			}
			kept.add(instrumentId);
		}
		int closed = jdbcTemplate.update(QueryConstants.CLOSE_MISSING_HOLDINGS, accountId, String.join(",", kept));
		LOGGER.info(WealthServiceConstants.EXITING + methodName + " | saved=" + kept.size() + " closed=" + closed);
		return new int[] { kept.size(), closed };
	}
}
