package com.wealth.bss.daoimplement;

import java.util.List;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.wealth.bss.common.model.dao.IfWealthAccountDao;
import com.wealth.bss.common.model.dto.PaginationDto;
import com.wealth.bss.common.model.entity.WealthAccountEntity;
import com.wealth.bss.common.model.entity.WealthAccountSubtypeEntity;
import com.wealth.bss.constants.WealthServiceConstants;
import com.wealth.bss.query.QueryConstants;
import com.wealth.common.logger.KarthiLogger;

@Repository
public class WealthAccountDaoImple extends WealthBaseDaoImple implements IfWealthAccountDao {

	private static final KarthiLogger LOGGER = new KarthiLogger(WealthAccountDaoImple.class);

	public WealthAccountDaoImple(JdbcTemplate jdbcTemplate) {
		super(jdbcTemplate);
	}

	@Override
	public List<WealthAccountEntity> findByUser(PaginationDto paginationDto) {
		String methodName = "findByUser";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		List<WealthAccountEntity> rows = jdbcTemplate.query(QueryConstants.FIND_ACCOUNTS_BY_USER,
				new BeanPropertyRowMapper<>(WealthAccountEntity.class), paginationDto.getUserId());
		LOGGER.info(WealthServiceConstants.EXITING + methodName + " | rows=" + rows.size());
		return rows;
	}

	@Override
	public WealthAccountEntity findById(PaginationDto paginationDto) {
		String methodName = "findById";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		try {
			return jdbcTemplate.queryForObject(QueryConstants.FIND_ACCOUNT_BY_ID,
					new BeanPropertyRowMapper<>(WealthAccountEntity.class), paginationDto.getUserId(), paginationDto.getId());
		} catch (EmptyResultDataAccessException ex) {
			LOGGER.info("No Record Found | " + methodName);
			return null;
		} finally {
			LOGGER.info(WealthServiceConstants.EXITING + methodName);
		}
	}

	@Override
	public List<WealthAccountSubtypeEntity> findAllSubtypes() {
		String methodName = "findAllSubtypes";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		List<WealthAccountSubtypeEntity> rows = jdbcTemplate.query(QueryConstants.FIND_ACCOUNT_SUBTYPES,
				new BeanPropertyRowMapper<>(WealthAccountSubtypeEntity.class));
		LOGGER.info(WealthServiceConstants.EXITING + methodName + " | rows=" + rows.size());
		return rows;
	}

	@Override
	@Transactional
	public WealthAccountEntity insert(WealthAccountEntity entity) {
		String methodName = "insert";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		setActor(entity);
		List<WealthAccountEntity> rows = jdbcTemplate.query(QueryConstants.INSERT_ACCOUNT, new BeanPropertyRowMapper<>(WealthAccountEntity.class),
				entity.getUserId(), entity.getAccountKey(), entity.getBankName(), entity.getAccountName(), entity.getSubtypeCode(), entity.getSubtypeCode(),
				entity.getMaskedIdentifier(), entity.getCurrency(), entity.getFdPrincipal(), sqlDate(entity.getFdBookedOn()),
				sqlDate(entity.getFdMaturityOn()), entity.getFdPayout(), entity.getFdCompounding());
		LOGGER.info(WealthServiceConstants.EXITING + methodName + " | inserted=" + rows.size());
		return rows.isEmpty() ? null : rows.get(0);
	}

	@Override
	@Transactional
	public WealthAccountEntity updateFdTerms(WealthAccountEntity entity) {
		String methodName = "updateFdTerms";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		setActor(entity);
		List<WealthAccountEntity> rows = jdbcTemplate.query(QueryConstants.UPDATE_ACCOUNT_FD_TERMS, new BeanPropertyRowMapper<>(WealthAccountEntity.class),
				entity.getFdPrincipal(), sqlDate(entity.getFdBookedOn()), sqlDate(entity.getFdMaturityOn()), entity.getFdPayout(),
				entity.getFdCompounding(), entity.getUserId(), entity.getId());
		LOGGER.info(WealthServiceConstants.EXITING + methodName + " | updated=" + rows.size());
		return rows.isEmpty() ? null : rows.get(0);
	}
}
