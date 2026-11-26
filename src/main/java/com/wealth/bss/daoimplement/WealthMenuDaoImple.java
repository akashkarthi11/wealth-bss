package com.wealth.bss.daoimplement;

import java.util.List;

import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.wealth.bss.common.model.dao.IfWealthMenuDao;
import com.wealth.bss.common.model.entity.WealthMenuEntity;
import com.wealth.bss.constants.WealthServiceConstants;
import com.wealth.bss.query.QueryConstants;
import com.wealth.common.logger.KarthiLogger;

@Repository
public class WealthMenuDaoImple extends WealthBaseDaoImple implements IfWealthMenuDao {

	private static final KarthiLogger LOGGER = new KarthiLogger(WealthMenuDaoImple.class);

	public WealthMenuDaoImple(JdbcTemplate jdbcTemplate) {
		super(jdbcTemplate);
	}

	@Override
	public List<WealthMenuEntity> findAll() {
		String methodName = "findAll";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		List<WealthMenuEntity> rows = jdbcTemplate.query(QueryConstants.FIND_MENUS, new BeanPropertyRowMapper<>(WealthMenuEntity.class));
		LOGGER.info(WealthServiceConstants.EXITING + methodName + " | rows=" + rows.size());
		return rows;
	}
}
