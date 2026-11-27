package com.wealth.bss.daoimplement;

import java.util.List;

import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.wealth.bss.common.model.dao.IfWealthConnectionDao;
import com.wealth.bss.common.model.entity.WealthConnectionEntity;
import com.wealth.bss.constants.WealthServiceConstants;
import com.wealth.bss.query.QueryConstants;
import com.wealth.common.logger.KarthiLogger;

@Repository
public class WealthConnectionDaoImple extends WealthBaseDaoImple implements IfWealthConnectionDao {

	private static final KarthiLogger LOGGER = new KarthiLogger(WealthConnectionDaoImple.class);

	public WealthConnectionDaoImple(JdbcTemplate jdbcTemplate) {
		super(jdbcTemplate);
	}

	@Override
	public List<WealthConnectionEntity> findAll() {
		String methodName = "findAll";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		List<WealthConnectionEntity> rows = jdbcTemplate.query(QueryConstants.FIND_CONNECTIONS, new BeanPropertyRowMapper<>(WealthConnectionEntity.class));
		LOGGER.info(WealthServiceConstants.EXITING + methodName + " | rows=" + rows.size());
		return rows;
	}

	@Override
	@Transactional
	public WealthConnectionEntity upsert(WealthConnectionEntity entity) {
		String methodName = "upsert";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		setActor(entity);
		List<WealthConnectionEntity> rows = jdbcTemplate.query(QueryConstants.UPSERT_CONNECTION, new BeanPropertyRowMapper<>(WealthConnectionEntity.class),
				entity.getProvider(), entity.getLabel(), entity.getBaseUrl(), entity.getCredentialEnc(), entity.getKeyHint(), entity.getCredentialExtraEnc(),
				entity.isEnabled(), entity.isAutoFetch(), entity.getOwnerUserId(), entity.getPriority());
		LOGGER.info(WealthServiceConstants.EXITING + methodName + " | saved=" + rows.size());
		return rows.isEmpty() ? null : rows.get(0);
	}

	@Override
	@Transactional
	public WealthConnectionEntity saveTestResult(WealthConnectionEntity entity) {
		String methodName = "saveTestResult";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		setActor(entity);
		List<WealthConnectionEntity> rows = jdbcTemplate.query(QueryConstants.UPDATE_CONNECTION_TEST, new BeanPropertyRowMapper<>(WealthConnectionEntity.class),
				entity.getLastStatus(), entity.getProvider());
		LOGGER.info(WealthServiceConstants.EXITING + methodName + " | updated=" + rows.size());
		return rows.isEmpty() ? null : rows.get(0);
	}

	@Override
	@Transactional
	public WealthConnectionEntity saveFetchResult(WealthConnectionEntity entity) {
		String methodName = "saveFetchResult";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		setActor(entity);
		List<WealthConnectionEntity> rows = jdbcTemplate.query(QueryConstants.UPDATE_CONNECTION_FETCH, new BeanPropertyRowMapper<>(WealthConnectionEntity.class),
				entity.getLastFetchNote(), entity.getProvider());
		LOGGER.info(WealthServiceConstants.EXITING + methodName + " | updated=" + rows.size());
		return rows.isEmpty() ? null : rows.get(0);
	}

	@Override
	@Transactional
	public boolean clearCredential(String provider) {
		String methodName = "clearCredential";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		setActor(new WealthConnectionEntity());
		int updated = jdbcTemplate.update(QueryConstants.CLEAR_CONNECTION_CREDENTIAL, provider);
		LOGGER.info(WealthServiceConstants.EXITING + methodName + " | updated=" + updated);
		return updated > 0;
	}

	@Override
	public List<com.wealth.common.model.dto.admin.ConnectionOverviewRowDto> overview(String part, String userId) {
		String methodName = "overview";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName + " | " + part);
		BeanPropertyRowMapper<com.wealth.common.model.dto.admin.ConnectionOverviewRowDto> mapper = new BeanPropertyRowMapper<>(com.wealth.common.model.dto.admin.ConnectionOverviewRowDto.class);
		List<com.wealth.common.model.dto.admin.ConnectionOverviewRowDto> rows;
		if ("common".equals(part)) {
			rows = jdbcTemplate.query(QueryConstants.OVERVIEW_COMMON_CONNECTIONS, mapper);
		} else if ("user".equals(part)) {
			String who = userId == null ? "" : userId;
			rows = jdbcTemplate.query(QueryConstants.OVERVIEW_USER_CONNECTIONS, mapper, who, who);
		} else {
			rows = jdbcTemplate.query(QueryConstants.OVERVIEW_MAIN_CONNECTIONS, mapper);
		}
		LOGGER.info(WealthServiceConstants.EXITING + methodName + " | rows=" + rows.size());
		return rows;
	}

	@Override
	@Transactional
	public boolean delete(String provider) {
		String methodName = "delete";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		setActor(new WealthConnectionEntity());
		int removed = jdbcTemplate.update(QueryConstants.DELETE_CONNECTION, provider);
		LOGGER.info(WealthServiceConstants.EXITING + methodName + " | removed=" + removed);
		return removed > 0;
	}
}
