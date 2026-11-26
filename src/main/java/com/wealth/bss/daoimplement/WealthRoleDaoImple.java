package com.wealth.bss.daoimplement;

import java.util.List;

import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.wealth.bss.common.model.dao.IfWealthRoleDao;
import com.wealth.bss.common.model.dto.PaginationDto;
import com.wealth.bss.common.model.entity.WealthRoleMenuEntity;
import com.wealth.bss.constants.WealthServiceConstants;
import com.wealth.bss.query.QueryConstants;
import com.wealth.common.logger.KarthiLogger;

@Repository
public class WealthRoleDaoImple extends WealthBaseDaoImple implements IfWealthRoleDao {

	private static final KarthiLogger LOGGER = new KarthiLogger(WealthRoleDaoImple.class);

	public WealthRoleDaoImple(JdbcTemplate jdbcTemplate) {
		super(jdbcTemplate);
	}

	@Override
	public long countUsersByRole(PaginationDto paginationDto) {
		String methodName = "countUsersByRole";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		Long count = jdbcTemplate.queryForObject(QueryConstants.COUNT_USERS_BY_ROLE, Long.class, paginationDto.getSearchText());
		LOGGER.info(WealthServiceConstants.EXITING + methodName);
		return count == null ? 0 : count;
	}

	@Override
	@Transactional
	public WealthRoleMenuEntity saveMenus(WealthRoleMenuEntity entity) {
		String methodName = "saveMenus";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		setActor(entity);
		List<WealthRoleMenuEntity> rows = jdbcTemplate.query(QueryConstants.UPSERT_ROLE_MENUS,
				new BeanPropertyRowMapper<>(WealthRoleMenuEntity.class), entity.getRole(), entity.getMenus(), entity.getUpdatedBy());
		LOGGER.info(WealthServiceConstants.EXITING + methodName + " | saved=" + rows.size());
		return rows.isEmpty() ? null : rows.get(0);
	}
}
