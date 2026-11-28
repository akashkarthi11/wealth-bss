package com.wealth.bss.daoimplement;

import java.util.List;

import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.wealth.bss.common.model.dao.IfWealthSmsDeviceDao;
import com.wealth.bss.common.model.entity.WealthSmsDeviceEntity;
import com.wealth.bss.constants.WealthServiceConstants;
import com.wealth.bss.query.QueryConstants;
import com.wealth.common.logger.KarthiLogger;

@Repository
public class WealthSmsDeviceDaoImple extends WealthBaseDaoImple implements IfWealthSmsDeviceDao {

	private static final KarthiLogger LOGGER = new KarthiLogger(WealthSmsDeviceDaoImple.class);

	public WealthSmsDeviceDaoImple(JdbcTemplate jdbcTemplate) {
		super(jdbcTemplate);
	}

	@Override
	public int countActive(String userId) {
		Integer count = jdbcTemplate.queryForObject(QueryConstants.COUNT_SMS_DEVICES, Integer.class, userId);
		return count == null ? 0 : count;
	}

	@Override
	@Transactional
	public String insert(String userId, String label, String tokenHash) {
		LOGGER.info(WealthServiceConstants.ENTERING + "insert sms device");
		setActor(new WealthSmsDeviceEntity());
		return jdbcTemplate.queryForObject(QueryConstants.INSERT_SMS_DEVICE, String.class, userId, label, tokenHash);
	}

	@Override
	public List<WealthSmsDeviceEntity> findActive(String userId) {
		return jdbcTemplate.query(QueryConstants.FIND_SMS_DEVICES, new BeanPropertyRowMapper<>(WealthSmsDeviceEntity.class), userId);
	}

	@Override
	@Transactional
	public boolean revoke(String userId, String id) {
		LOGGER.info(WealthServiceConstants.ENTERING + "revoke sms device");
		setActor(new WealthSmsDeviceEntity());
		return jdbcTemplate.update(QueryConstants.REVOKE_SMS_DEVICE, userId, id) > 0;
	}

	@Override
	public WealthSmsDeviceEntity findByTokenHash(String tokenHash) {
		List<WealthSmsDeviceEntity> rows = jdbcTemplate.query(QueryConstants.FIND_SMS_DEVICE_BY_TOKEN, new BeanPropertyRowMapper<>(WealthSmsDeviceEntity.class), tokenHash);
		return rows.isEmpty() ? null : rows.get(0);
	}

	@Override
	@Transactional
	public void touch(String id, int read, int saved) {
		WealthSmsDeviceEntity actor = new WealthSmsDeviceEntity();
		actor.setActor(WealthServiceConstants.ACTOR_SMS_DEVICE);
		setActor(actor);
		jdbcTemplate.update(QueryConstants.TOUCH_SMS_DEVICE, read, saved, id);
	}
}
