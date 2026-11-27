package com.wealth.bss.daoimplement;

import java.util.List;

import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.wealth.bss.common.model.dao.IfWealthCommonConnectionDao;
import com.wealth.bss.common.model.entity.WealthCommonConnectionEntity;
import com.wealth.bss.constants.WealthServiceConstants;
import com.wealth.bss.query.QueryConstants;
import com.wealth.common.logger.KarthiLogger;

@Repository
public class WealthCommonConnectionDaoImple extends WealthBaseDaoImple implements IfWealthCommonConnectionDao {

	private static final KarthiLogger LOGGER = new KarthiLogger(WealthCommonConnectionDaoImple.class);

	public WealthCommonConnectionDaoImple(JdbcTemplate jdbcTemplate) {
		super(jdbcTemplate);
	}

	@Override
	public WealthCommonConnectionEntity find(String provider) {
		String methodName = "find";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		List<WealthCommonConnectionEntity> rows = jdbcTemplate.query(QueryConstants.FIND_COMMON_CONNECTION,
				new BeanPropertyRowMapper<>(WealthCommonConnectionEntity.class), provider);
		LOGGER.info(WealthServiceConstants.EXITING + methodName + " | rows=" + rows.size());
		return rows.isEmpty() ? null : rows.get(0);
	}

	@Override
	@Transactional
	public WealthCommonConnectionEntity upsert(WealthCommonConnectionEntity entity) {
		String methodName = "upsert";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		setActor(entity);
		List<WealthCommonConnectionEntity> rows = jdbcTemplate.query(QueryConstants.UPSERT_COMMON_CONNECTION,
				new BeanPropertyRowMapper<>(WealthCommonConnectionEntity.class), entity.getProvider(), entity.getCredentialEnc(), entity.getKeyHint());
		LOGGER.info(WealthServiceConstants.EXITING + methodName + " | saved=" + rows.size());
		return rows.isEmpty() ? null : rows.get(0);
	}

	@Override
	@Transactional
	public boolean delete(String provider) {
		String methodName = "delete";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		setActor(new WealthCommonConnectionEntity());
		int removed = jdbcTemplate.update(QueryConstants.DELETE_COMMON_CONNECTION, provider);
		LOGGER.info(WealthServiceConstants.EXITING + methodName + " | removed=" + removed);
		return removed > 0;
	}
}
