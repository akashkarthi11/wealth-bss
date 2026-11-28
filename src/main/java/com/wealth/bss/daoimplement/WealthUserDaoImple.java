package com.wealth.bss.daoimplement;

import java.util.List;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.wealth.bss.common.model.dao.IfWealthUserDao;
import com.wealth.bss.common.model.dto.PaginationDto;
import com.wealth.bss.common.model.entity.WealthUserCredentialEntity;
import com.wealth.bss.common.model.entity.WealthUserEntity;
import com.wealth.bss.constants.WealthServiceConstants;
import com.wealth.bss.query.QueryConstants;
import com.wealth.common.logger.KarthiLogger;

@Repository
public class WealthUserDaoImple extends WealthBaseDaoImple implements IfWealthUserDao {

	private static final KarthiLogger LOGGER = new KarthiLogger(WealthUserDaoImple.class);

	public WealthUserDaoImple(JdbcTemplate jdbcTemplate) {
		super(jdbcTemplate);
	}

	@Override
	@Transactional
	public WealthUserEntity updateProfile(WealthUserEntity entity) {
		String methodName = "updateProfile";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		setActor(entity);
		List<WealthUserEntity> rows = jdbcTemplate.query(QueryConstants.UPDATE_WEALTH_USER_PROFILE, new BeanPropertyRowMapper<>(WealthUserEntity.class), entity.getDisplayName(), entity.getMobile(),
				entity.getPrefDateFormat(), entity.getPrefHome(), entity.getId());
		LOGGER.info(WealthServiceConstants.EXITING + methodName + " | updated=" + rows.size());
		return rows.isEmpty() ? null : rows.get(0);
	}

	@Override
	public WealthUserEntity findById(PaginationDto paginationDto) {
		String methodName = "findById";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		try {
			return jdbcTemplate.queryForObject(QueryConstants.FIND_WEALTH_USER_BY_ID,
					new BeanPropertyRowMapper<>(WealthUserEntity.class), paginationDto.getId());
		} catch (EmptyResultDataAccessException ex) {
			LOGGER.info("No Record Found | " + methodName);
			return null;
		} finally {
			LOGGER.info(WealthServiceConstants.EXITING + methodName);
		}
	}

	@Override
	public WealthUserCredentialEntity findCredentialByEmail(PaginationDto paginationDto) {
		String methodName = "findCredentialByEmail";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		try {
			return jdbcTemplate.queryForObject(QueryConstants.FIND_CREDENTIAL_BY_EMAIL,
					new BeanPropertyRowMapper<>(WealthUserCredentialEntity.class), paginationDto.getSearchText());
		} catch (EmptyResultDataAccessException ex) {
			LOGGER.info("No Record Found | " + methodName);
			return null;
		} finally {
			LOGGER.info(WealthServiceConstants.EXITING + methodName);
		}
	}

	@Override
	public WealthUserEntity insertUser(WealthUserCredentialEntity credential) {
		String methodName = "insertUser";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		List<WealthUserEntity> rows = jdbcTemplate.query(QueryConstants.INSERT_WEALTH_USER,
				new BeanPropertyRowMapper<>(WealthUserEntity.class), credential.getEmail(),
				credential.getPasswordHash());
		LOGGER.info(WealthServiceConstants.EXITING + methodName + " | inserted=" + rows.size());
		return rows.isEmpty() ? null : rows.get(0);
	}

	@Override
	public List<WealthUserEntity> findAll() {
		String methodName = "findAll";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		List<WealthUserEntity> rows = jdbcTemplate.query(QueryConstants.FIND_ALL_USERS, new BeanPropertyRowMapper<>(WealthUserEntity.class));
		LOGGER.info(WealthServiceConstants.EXITING + methodName + " | rows=" + rows.size());
		return rows;
	}

	@Override
	public WealthUserEntity updateRole(WealthUserEntity entity) {
		String methodName = "updateRole";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		List<WealthUserEntity> rows = jdbcTemplate.query(QueryConstants.UPDATE_USER_ROLE, new BeanPropertyRowMapper<>(WealthUserEntity.class), entity.getRole(),
				entity.getId());
		LOGGER.info(WealthServiceConstants.EXITING + methodName + " | updated=" + rows.size());
		return rows.isEmpty() ? null : rows.get(0);
	}

	@Override
	public WealthUserEntity updatePassword(WealthUserCredentialEntity credential) {
		String methodName = "updatePassword";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		List<WealthUserEntity> rows = jdbcTemplate.query(QueryConstants.UPDATE_WEALTH_USER_PASSWORD,
				new BeanPropertyRowMapper<>(WealthUserEntity.class), credential.getPasswordHash(),
				credential.getEmail());
		LOGGER.info(WealthServiceConstants.EXITING + methodName + " | updated=" + rows.size());
		return rows.isEmpty() ? null : rows.get(0);
	}

	@Override
	public String findMenusByRole(PaginationDto paginationDto) {
		String methodName = "findMenusByRole";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		List<String> rows = jdbcTemplate.queryForList(QueryConstants.FIND_MENUS_BY_ROLE, String.class,
				paginationDto.getSearchText());
		LOGGER.info(WealthServiceConstants.EXITING + methodName);
		return rows.isEmpty() ? null : rows.get(0);
	}
}
