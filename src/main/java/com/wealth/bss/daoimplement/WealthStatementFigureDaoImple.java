package com.wealth.bss.daoimplement;

import java.util.List;

import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.wealth.bss.common.model.dao.IfWealthStatementFigureDao;
import com.wealth.bss.common.model.entity.WealthStatementFigureEntity;
import com.wealth.bss.constants.WealthServiceConstants;
import com.wealth.bss.query.QueryConstants;
import com.wealth.common.logger.KarthiLogger;

@Repository
public class WealthStatementFigureDaoImple extends WealthBaseDaoImple implements IfWealthStatementFigureDao {

	private static final KarthiLogger LOGGER = new KarthiLogger(WealthStatementFigureDaoImple.class);

	public WealthStatementFigureDaoImple(JdbcTemplate jdbcTemplate) {
		super(jdbcTemplate);
	}

	@Override
	@Transactional
	public void upsert(WealthStatementFigureEntity entity) {
		String methodName = "upsert";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		setActor(entity);
		int saved = jdbcTemplate.update(QueryConstants.UPSERT_STATEMENT_FIGURES, entity.getUserId(), entity.getMailRef(), entity.getSource(), entity.getFigures() == null ? "{}" : entity.getFigures(),
				entity.getOrigin() == null ? WealthServiceConstants.STATEMENT_ORIGIN_GMAIL : entity.getOrigin(), entity.getStatus() == null ? WealthServiceConstants.STATEMENT_OPENED : entity.getStatus(),
				entity.getFileName(), entity.getLines() == null ? "[]" : entity.getLines());
		LOGGER.info(WealthServiceConstants.EXITING + methodName + " | saved=" + saved);
	}

	@Override
	@Transactional
	public void markAccepted(String userId, String id) {
		setActor(new WealthStatementFigureEntity());
		jdbcTemplate.update(QueryConstants.MARK_STATEMENT_ACCEPTED, userId, id);
	}

	@Override
	public List<WealthStatementFigureEntity> findHistory(String userId) {
		return jdbcTemplate.query(QueryConstants.FIND_STATEMENT_HISTORY, new BeanPropertyRowMapper<>(WealthStatementFigureEntity.class), userId);
	}

	@Override
	public WealthStatementFigureEntity find(String userId, String id) {
		List<WealthStatementFigureEntity> rows = jdbcTemplate.query(QueryConstants.FIND_STATEMENT_BY_ID, new BeanPropertyRowMapper<>(WealthStatementFigureEntity.class), userId, id);
		return rows.isEmpty() ? null : rows.get(0);
	}

	@Override
	public List<WealthStatementFigureEntity> findByUser(String userId) {
		String methodName = "findByUser";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		List<WealthStatementFigureEntity> rows = jdbcTemplate.query(QueryConstants.FIND_STATEMENT_FIGURES, new BeanPropertyRowMapper<>(WealthStatementFigureEntity.class), userId);
		LOGGER.info(WealthServiceConstants.EXITING + methodName + " | rows=" + rows.size());
		return rows;
	}
}
