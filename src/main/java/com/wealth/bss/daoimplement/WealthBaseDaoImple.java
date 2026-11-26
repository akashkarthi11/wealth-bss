package com.wealth.bss.daoimplement;

import java.util.Date;

import org.springframework.jdbc.core.JdbcTemplate;

import com.wealth.bss.common.model.entity.GenericEntity;
import com.wealth.bss.constants.WealthServiceConstants;
import com.wealth.bss.query.QueryConstants;
import com.wealth.common.utils.generic.RequestHeaderUtil;

// What every write DaoImple shares: the JdbcTemplate and the writer's identity.
public abstract class WealthBaseDaoImple {

	protected final JdbcTemplate jdbcTemplate;

	protected WealthBaseDaoImple(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	// First statement of every write, in the same transaction: the audit log, the external-balance guard and the crtd_by / updt_by trigger
	// read wealth.actor and wealth.user_id. Cron writes carry no user: the trigger records them as the system user 1.
	protected void setActor(GenericEntity entity) {
		String actor = entity.getActor() == null ? WealthServiceConstants.ACTOR_OWNER : entity.getActor();
		String userId = WealthServiceConstants.ACTOR_CRON.equals(actor) ? null : RequestHeaderUtil.getUserId();
		jdbcTemplate.queryForObject(QueryConstants.SET_ACTOR, String.class, actor, userId == null ? "" : userId);
	}

	// The PostgreSQL driver takes java.sql.Date, not java.util.Date.
	protected static java.sql.Date sqlDate(Date date) {
		return date == null ? null : new java.sql.Date(date.getTime());
	}
}
