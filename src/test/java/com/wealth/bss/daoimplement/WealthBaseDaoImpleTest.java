package com.wealth.bss.daoimplement;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import com.wealth.bss.common.model.entity.WealthAccountEntity;
import com.wealth.bss.constants.WealthServiceConstants;
import com.wealth.bss.query.QueryConstants;
import com.wealth.common.utils.generic.RequestHeaderUtil;

class WealthBaseDaoImpleTest {

	private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
	private final WealthBaseDaoImple dao = new WealthAccountDaoImple(jdbc);

	@AfterEach
	void tearDown() {
		RequestHeaderUtil.clear();
	}

	@Test
	void aSignedInWriterIsPassedToTheDatabaseForTheAuditColumns() {
		RequestHeaderUtil.setUserId("user-7");
		dao.setActor(new WealthAccountEntity());
		verify(jdbc).queryForObject(QueryConstants.SET_ACTOR, String.class, WealthServiceConstants.ACTOR_OWNER, "user-7");
	}

	@Test
	void aCronWriteCarriesNoUserSoTheDatabaseRecordsTheSystemUser() {
		RequestHeaderUtil.setUserId("user-7");
		WealthAccountEntity entity = new WealthAccountEntity();
		entity.setActor(WealthServiceConstants.ACTOR_CRON);
		dao.setActor(entity);
		verify(jdbc).queryForObject(QueryConstants.SET_ACTOR, String.class, WealthServiceConstants.ACTOR_CRON, "");
	}
}
