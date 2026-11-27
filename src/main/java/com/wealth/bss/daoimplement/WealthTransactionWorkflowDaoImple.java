package com.wealth.bss.daoimplement;

import java.util.List;

import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.wealth.bss.common.model.dao.IfWealthTransactionWorkflowDao;
import com.wealth.bss.common.model.entity.WealthTransactionWorkflowEntity;
import com.wealth.bss.constants.WealthServiceConstants;
import com.wealth.bss.query.QueryConstants;
import com.wealth.common.logger.KarthiLogger;

@Repository
public class WealthTransactionWorkflowDaoImple extends WealthBaseDaoImple implements IfWealthTransactionWorkflowDao {

	private static final KarthiLogger LOGGER = new KarthiLogger(WealthTransactionWorkflowDaoImple.class);

	public WealthTransactionWorkflowDaoImple(JdbcTemplate jdbcTemplate) {
		super(jdbcTemplate);
	}

	@Override
	@Transactional
	public boolean insert(WealthTransactionWorkflowEntity entity) {
		String methodName = "insert";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		setActor(entity);
		List<String> ids = jdbcTemplate.queryForList(QueryConstants.INSERT_WORKFLOW, String.class, entity.getUserId(), entity.getSource(), entity.getSourceRef(), entity.getOccurredOn(), entity.getDescription(),
				entity.getAmount(), entity.getDirection(), entity.getAccountId(), entity.getAccountHint(), entity.getReason(), entity.getCandidateIds() == null ? "" : entity.getCandidateIds());
		LOGGER.info(WealthServiceConstants.EXITING + methodName + " | stored=" + ids.size());
		return !ids.isEmpty();
	}

	@Override
	public boolean exists(String userId, String sourceRef) {
		Integer count = jdbcTemplate.queryForObject(QueryConstants.COUNT_WORKFLOW_REFERENCE, Integer.class, userId, sourceRef);
		return count != null && count > 0;
	}

	@Override
	public List<WealthTransactionWorkflowEntity> findOpen(String userId) {
		return jdbcTemplate.query(QueryConstants.FIND_OPEN_WORKFLOW, new BeanPropertyRowMapper<>(WealthTransactionWorkflowEntity.class), userId);
	}

	@Override
	public WealthTransactionWorkflowEntity find(String userId, String id) {
		List<WealthTransactionWorkflowEntity> rows = jdbcTemplate.query(QueryConstants.FIND_WORKFLOW, new BeanPropertyRowMapper<>(WealthTransactionWorkflowEntity.class), userId, id);
		return rows.isEmpty() ? null : rows.get(0);
	}

	@Override
	@Transactional
	public boolean decide(String userId, String id, String status) {
		String methodName = "decide";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		WealthTransactionWorkflowEntity who = new WealthTransactionWorkflowEntity();
		setActor(who);
		List<String> ids = jdbcTemplate.queryForList(QueryConstants.DECIDE_WORKFLOW, String.class, status, userId, userId, id);
		LOGGER.info(WealthServiceConstants.EXITING + methodName + " | decided=" + ids.size());
		return !ids.isEmpty();
	}
}
