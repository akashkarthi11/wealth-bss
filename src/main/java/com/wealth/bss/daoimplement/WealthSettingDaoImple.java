package com.wealth.bss.daoimplement;

import java.util.List;

import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.wealth.bss.common.model.dao.IfWealthSettingDao;
import com.wealth.bss.common.model.entity.WealthSettingEntity;
import com.wealth.bss.constants.WealthServiceConstants;
import com.wealth.bss.query.QueryConstants;
import com.wealth.common.logger.KarthiLogger;

@Repository
public class WealthSettingDaoImple extends WealthBaseDaoImple implements IfWealthSettingDao {

	private static final KarthiLogger LOGGER = new KarthiLogger(WealthSettingDaoImple.class);

	public WealthSettingDaoImple(JdbcTemplate jdbcTemplate) {
		super(jdbcTemplate);
	}

	@Override
	public List<WealthSettingEntity> findAll() {
		String methodName = "findAll";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		List<WealthSettingEntity> rows = jdbcTemplate.query(QueryConstants.FIND_SETTINGS, new BeanPropertyRowMapper<>(WealthSettingEntity.class));
		LOGGER.info(WealthServiceConstants.EXITING + methodName + " | rows=" + rows.size());
		return rows;
	}

	@Override
	@Transactional
	public WealthSettingEntity upsert(WealthSettingEntity entity) {
		String methodName = "upsert";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		setActor(entity);
		List<WealthSettingEntity> rows = jdbcTemplate.query(QueryConstants.UPSERT_SETTING, new BeanPropertyRowMapper<>(WealthSettingEntity.class),
				entity.getSettingKey(), entity.getSettingValue(), entity.getUsername(), entity.getPasswordEnc());
		LOGGER.info(WealthServiceConstants.EXITING + methodName + " | saved=" + rows.size());
		return rows.isEmpty() ? null : rows.get(0);
	}

	@Override
	@Transactional
	public WealthSettingEntity insertIfMissing(WealthSettingEntity entity) {
		String methodName = "insertIfMissing";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		setActor(entity);
		jdbcTemplate.update(QueryConstants.INSERT_SETTING_IF_MISSING, entity.getSettingKey(), entity.getSettingValue(), entity.getUsername(),
				entity.getPasswordEnc());
		List<WealthSettingEntity> rows = jdbcTemplate.query(QueryConstants.FIND_SETTING, new BeanPropertyRowMapper<>(WealthSettingEntity.class),
				entity.getSettingKey());
		LOGGER.info(WealthServiceConstants.EXITING + methodName);
		return rows.isEmpty() ? null : rows.get(0);
	}
}
