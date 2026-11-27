package com.wealth.bss.daoimplement;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;

import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.wealth.bss.common.model.dao.IfWealthApiUsageDao;
import com.wealth.bss.common.model.entity.WealthApiUsageEntity;
import com.wealth.bss.constants.WealthServiceConstants;
import com.wealth.bss.query.QueryConstants;
import com.wealth.common.logger.KarthiLogger;

@Repository
public class WealthApiUsageDaoImple extends WealthBaseDaoImple implements IfWealthApiUsageDao {

	private static final KarthiLogger LOGGER = new KarthiLogger(WealthApiUsageDaoImple.class);

	public WealthApiUsageDaoImple(JdbcTemplate jdbcTemplate) {
		super(jdbcTemplate);
	}

	@Override
	@Transactional
	public boolean saveBatch(String batchId, List<WealthApiUsageEntity> rows) {
		String methodName = "saveBatch";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName + " | rows=" + rows.size());
		WealthApiUsageEntity writer = new WealthApiUsageEntity();
		writer.setActor(WealthServiceConstants.ACTOR_CRON);
		setActor(writer);
		if (jdbcTemplate.update(QueryConstants.INSERT_API_USAGE_BATCH, batchId) == 0) {
			LOGGER.info(WealthServiceConstants.EXITING + methodName + " | already saved");
			return false;
		}
		for (WealthApiUsageEntity row : rows) {
			jdbcTemplate.update(QueryConstants.UPSERT_API_USAGE, Date.valueOf(row.getDay()), row.getKind(), row.getName(), row.getUserId() == null ? WealthServiceConstants.USAGE_NO_USER_ID : row.getUserId(), row.getCalls(), row.getSuccess(),
					row.getClientErrors(), row.getFailures(), row.getTotalMs());
		}
		jdbcTemplate.update(QueryConstants.DELETE_OLD_API_USAGE_BATCHES);
		LOGGER.info(WealthServiceConstants.EXITING + methodName);
		return true;
	}

	@Override
	public boolean isBatchSaved(String batchId) {
		Integer found = jdbcTemplate.queryForObject(QueryConstants.COUNT_API_USAGE_BATCH, Integer.class, batchId);
		return found != null && found > 0;
	}

	@Override
	public List<WealthApiUsageEntity> findTotalsSince(LocalDate from, String userId) {
		String methodName = "findTotalsSince";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		List<WealthApiUsageEntity> rows = jdbcTemplate.query(QueryConstants.FIND_API_USAGE_TOTALS_SINCE, new BeanPropertyRowMapper<>(WealthApiUsageEntity.class),
				Date.valueOf(from), userId, userId);
		LOGGER.info(WealthServiceConstants.EXITING + methodName + " | rows=" + rows.size());
		return rows;
	}

	@Override
	public List<WealthApiUsageEntity> findUserCallsSince(LocalDate from) {
		return jdbcTemplate.query(QueryConstants.FIND_API_USAGE_USER_CALLS_SINCE, new BeanPropertyRowMapper<>(WealthApiUsageEntity.class), Date.valueOf(from),
				WealthServiceConstants.USAGE_NO_USER_ID);
	}

	@Override
	public List<WealthApiUsageEntity> findUsers() {
		return jdbcTemplate.query(QueryConstants.FIND_API_USAGE_USERS, new BeanPropertyRowMapper<>(WealthApiUsageEntity.class));
	}
}
