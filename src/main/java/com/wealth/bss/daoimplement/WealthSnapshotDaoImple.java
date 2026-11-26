package com.wealth.bss.daoimplement;

import java.util.List;

import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.wealth.bss.common.model.dao.IfWealthSnapshotDao;
import com.wealth.bss.common.model.dto.PaginationDto;
import com.wealth.bss.common.model.entity.WealthSnapshotEntity;
import com.wealth.bss.constants.WealthServiceConstants;
import com.wealth.bss.query.QueryConstants;
import com.wealth.common.logger.KarthiLogger;

@Repository
public class WealthSnapshotDaoImple implements IfWealthSnapshotDao {

	private static final KarthiLogger LOGGER = new KarthiLogger(WealthSnapshotDaoImple.class);

	private final JdbcTemplate jdbcTemplate;

	public WealthSnapshotDaoImple(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public List<WealthSnapshotEntity> findByUser(PaginationDto paginationDto) {
		String methodName = "findByUser";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		String text = paginationDto.getSearchText();
		String pattern = text == null || text.isBlank() ? null : "%" + text.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
		List<WealthSnapshotEntity> rows = jdbcTemplate.query(QueryConstants.FIND_SNAPSHOTS_BY_USER,
				new BeanPropertyRowMapper<>(WealthSnapshotEntity.class), paginationDto.getUserId(), pattern, pattern, pattern);
		LOGGER.info(WealthServiceConstants.EXITING + methodName + " | rows=" + rows.size());
		return rows;
	}
}
