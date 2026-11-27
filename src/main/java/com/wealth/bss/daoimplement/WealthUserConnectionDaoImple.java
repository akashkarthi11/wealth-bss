package com.wealth.bss.daoimplement;

import java.util.List;

import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.wealth.bss.common.model.dao.IfWealthUserConnectionDao;
import com.wealth.bss.common.model.entity.WealthUserConnectionEntity;
import com.wealth.bss.constants.WealthServiceConstants;
import com.wealth.bss.query.QueryConstants;
import com.wealth.common.logger.KarthiLogger;

@Repository
public class WealthUserConnectionDaoImple extends WealthBaseDaoImple implements IfWealthUserConnectionDao {

	private static final KarthiLogger LOGGER = new KarthiLogger(WealthUserConnectionDaoImple.class);

	public WealthUserConnectionDaoImple(JdbcTemplate jdbcTemplate) {
		super(jdbcTemplate);
	}

	@Override
	public WealthUserConnectionEntity find(String userId, String provider) {
		String methodName = "find";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		List<WealthUserConnectionEntity> rows = jdbcTemplate.query(QueryConstants.FIND_USER_CONNECTION,
				new BeanPropertyRowMapper<>(WealthUserConnectionEntity.class), userId, provider);
		LOGGER.info(WealthServiceConstants.EXITING + methodName + " | rows=" + rows.size());
		return rows.isEmpty() ? null : rows.get(0);
	}

	@Override
	public List<WealthUserConnectionEntity> findByProvider(String provider) {
		return jdbcTemplate.query(QueryConstants.FIND_USER_CONNECTIONS_BY_PROVIDER, new BeanPropertyRowMapper<>(WealthUserConnectionEntity.class), provider);
	}

	@Override
	@Transactional
	public WealthUserConnectionEntity upsert(WealthUserConnectionEntity entity) {
		String methodName = "upsert";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		setActor(entity);
		List<WealthUserConnectionEntity> rows = jdbcTemplate.query(QueryConstants.UPSERT_USER_CONNECTION,
				new BeanPropertyRowMapper<>(WealthUserConnectionEntity.class), entity.getUserId(), entity.getProvider(), entity.getCredentialEnc(),
				entity.getKeyHint(), entity.isEnabled());
		LOGGER.info(WealthServiceConstants.EXITING + methodName + " | saved=" + rows.size());
		return rows.isEmpty() ? null : rows.get(0);
	}

	@Override
	@Transactional
	public WealthUserConnectionEntity saveCredential(String userId, String provider, String credentialEnc) {
		String methodName = "saveCredential";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		setActor(new WealthUserConnectionEntity());
		List<WealthUserConnectionEntity> rows = jdbcTemplate.query(QueryConstants.UPDATE_USER_CONNECTION_CREDENTIAL,
				new BeanPropertyRowMapper<>(WealthUserConnectionEntity.class), credentialEnc, userId, provider);
		LOGGER.info(WealthServiceConstants.EXITING + methodName + " | updated=" + rows.size());
		return rows.isEmpty() ? null : rows.get(0);
	}

	@Override
	@Transactional
	public WealthUserConnectionEntity saveCheck(String userId, String provider, String status) {
		String methodName = "saveCheck";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		setActor(new WealthUserConnectionEntity());
		List<WealthUserConnectionEntity> rows = jdbcTemplate.query(QueryConstants.UPDATE_USER_CONNECTION_CHECK,
				new BeanPropertyRowMapper<>(WealthUserConnectionEntity.class), status, userId, provider);
		LOGGER.info(WealthServiceConstants.EXITING + methodName + " | updated=" + rows.size());
		return rows.isEmpty() ? null : rows.get(0);
	}

	@Override
	@Transactional
	public boolean delete(String userId, String provider) {
		String methodName = "delete";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		setActor(new WealthUserConnectionEntity());
		int removed = jdbcTemplate.update(QueryConstants.DELETE_USER_CONNECTION, userId, provider);
		LOGGER.info(WealthServiceConstants.EXITING + methodName + " | removed=" + removed);
		return removed > 0;
	}
}
