package com.wealth.bss.daoimplement;

import java.util.List;

import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.wealth.bss.common.model.dao.IfWealthAccountSplitDao;
import com.wealth.bss.common.model.dto.PaginationDto;
import com.wealth.bss.common.model.entity.WealthAccountSplitBalanceEntity;
import com.wealth.bss.common.model.entity.WealthAccountSplitEntity;
import com.wealth.bss.common.model.entity.WealthAccountSplitMoveEntity;
import com.wealth.bss.constants.WealthServiceConstants;
import com.wealth.bss.query.QueryConstants;
import com.wealth.common.logger.KarthiLogger;

@Repository
public class WealthAccountSplitDaoImple implements IfWealthAccountSplitDao {

	private static final KarthiLogger LOGGER = new KarthiLogger(WealthAccountSplitDaoImple.class);

	private final JdbcTemplate jdbcTemplate;

	public WealthAccountSplitDaoImple(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public List<WealthAccountSplitEntity> findByAccount(PaginationDto paginationDto) {
		String methodName = "findByAccount";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		List<WealthAccountSplitEntity> rows = jdbcTemplate.query(QueryConstants.FIND_SPLITS_BY_ACCOUNT,
				new BeanPropertyRowMapper<>(WealthAccountSplitEntity.class), paginationDto.getUserId(), paginationDto.getId());
		LOGGER.info(WealthServiceConstants.EXITING + methodName + " | rows=" + rows.size());
		return rows;
	}

	@Override
	public List<WealthAccountSplitBalanceEntity> findLatestBalancesByAccount(PaginationDto paginationDto) {
		String methodName = "findLatestBalancesByAccount";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		List<WealthAccountSplitBalanceEntity> rows = jdbcTemplate.query(QueryConstants.FIND_LATEST_SPLIT_BALANCES_BY_ACCOUNT,
				new BeanPropertyRowMapper<>(WealthAccountSplitBalanceEntity.class), paginationDto.getUserId(), paginationDto.getId());
		LOGGER.info(WealthServiceConstants.EXITING + methodName + " | rows=" + rows.size());
		return rows;
	}

	@Override
	public List<WealthAccountSplitMoveEntity> findMovesByAccount(PaginationDto paginationDto) {
		String methodName = "findMovesByAccount";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		List<WealthAccountSplitMoveEntity> rows = jdbcTemplate.query(QueryConstants.FIND_SPLIT_MOVES_BY_ACCOUNT,
				new BeanPropertyRowMapper<>(WealthAccountSplitMoveEntity.class), paginationDto.getUserId(), paginationDto.getId());
		LOGGER.info(WealthServiceConstants.EXITING + methodName + " | rows=" + rows.size());
		return rows;
	}
}
