package com.wealth.bss.daoimplement;

import java.util.List;

import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.wealth.bss.common.model.dao.IfWealthAccountBalanceDao;
import com.wealth.bss.common.model.dto.PaginationDto;
import com.wealth.bss.common.model.entity.WealthAccountBalanceEntity;
import com.wealth.bss.constants.WealthServiceConstants;
import com.wealth.bss.query.QueryConstants;
import com.wealth.common.logger.KarthiLogger;

@Repository
public class WealthAccountBalanceDaoImple extends WealthBaseDaoImple implements IfWealthAccountBalanceDao {

	private static final KarthiLogger LOGGER = new KarthiLogger(WealthAccountBalanceDaoImple.class);

	public WealthAccountBalanceDaoImple(JdbcTemplate jdbcTemplate) {
		super(jdbcTemplate);
	}

	@Override
	public List<WealthAccountBalanceEntity> findLatestByUser(PaginationDto paginationDto) {
		String methodName = "findLatestByUser";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		List<WealthAccountBalanceEntity> rows = jdbcTemplate.query(QueryConstants.FIND_LATEST_BALANCES_BY_USER,
				new BeanPropertyRowMapper<>(WealthAccountBalanceEntity.class), paginationDto.getUserId());
		LOGGER.info(WealthServiceConstants.EXITING + methodName + " | rows=" + rows.size());
		return rows;
	}

	@Override
	public List<WealthAccountBalanceEntity> findHistory(PaginationDto paginationDto) {
		String methodName = "findHistory";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		int limit = paginationDto.getPageSize() > 0 ? paginationDto.getPageSize() : WealthServiceConstants.BALANCE_HISTORY_DEFAULT;
		List<WealthAccountBalanceEntity> rows = jdbcTemplate.query(QueryConstants.FIND_BALANCE_HISTORY,
				new BeanPropertyRowMapper<>(WealthAccountBalanceEntity.class), paginationDto.getUserId(), paginationDto.getId(), limit);
		LOGGER.info(WealthServiceConstants.EXITING + methodName + " | rows=" + rows.size());
		return rows;
	}

	@Override
	@Transactional
	public WealthAccountBalanceEntity insert(WealthAccountBalanceEntity entity) {
		String methodName = "insert";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		setActor(entity);
		List<WealthAccountBalanceEntity> rows = jdbcTemplate.query(QueryConstants.INSERT_ACCOUNT_BALANCE,
				new BeanPropertyRowMapper<>(WealthAccountBalanceEntity.class), entity.getAsOf(), entity.getBalance(), entity.getStatus(),
				entity.getNote(), entity.getEnteredBy(), entity.getUserId(), entity.getAccountId());
		LOGGER.info(WealthServiceConstants.EXITING + methodName + " | inserted=" + rows.size());
		return rows.isEmpty() ? null : rows.get(0);
	}

	@Override
	@Transactional
	public boolean updateAccountCopy(WealthAccountBalanceEntity entity) {
		String methodName = "updateAccountCopy";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		setActor(entity);
		List<String> rows = jdbcTemplate.queryForList(QueryConstants.UPDATE_ACCOUNT_BALANCE_COPY, String.class, entity.getBalance(), entity.getAsOf(),
				entity.getStatus(), entity.getUserId(), entity.getAccountId(), entity.getAsOf());
		LOGGER.info(WealthServiceConstants.EXITING + methodName + " | updated=" + rows.size());
		return !rows.isEmpty();
	}
}
