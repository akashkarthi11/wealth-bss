package com.wealth.bss.daoimplement;

import java.util.List;

import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.wealth.bss.common.model.dao.IfWealthPeopleDao;
import com.wealth.bss.common.model.dto.PaginationDto;
import com.wealth.bss.common.model.entity.WealthBillSplitEntity;
import com.wealth.bss.common.model.entity.WealthBillSplitShareEntity;
import com.wealth.bss.common.model.entity.WealthLendCommentEntity;
import com.wealth.bss.common.model.entity.WealthLendRequestEntity;
import com.wealth.bss.common.model.entity.WealthPersonAliasEntity;
import com.wealth.bss.common.model.entity.WealthPersonEntity;
import com.wealth.bss.common.model.entity.WealthPersonEntryEntity;
import com.wealth.bss.common.model.entity.WealthPersonRollupEntity;
import com.wealth.bss.common.model.entity.WealthTransactionEntity;
import com.wealth.bss.constants.WealthServiceConstants;
import com.wealth.bss.query.QueryConstants;
import com.wealth.common.logger.KarthiLogger;

@Repository
public class WealthPeopleDaoImple extends WealthBaseDaoImple implements IfWealthPeopleDao {

	private static final KarthiLogger LOGGER = new KarthiLogger(WealthPeopleDaoImple.class);

	public WealthPeopleDaoImple(JdbcTemplate jdbcTemplate) {
		super(jdbcTemplate);
	}

	private <T> List<T> read(String methodName, String sql, Class<T> type, PaginationDto paginationDto) {
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		List<T> rows = jdbcTemplate.query(sql, new BeanPropertyRowMapper<>(type), paginationDto.getUserId());
		LOGGER.info(WealthServiceConstants.EXITING + methodName + " | rows=" + rows.size());
		return rows;
	}

	@Override
	public List<WealthPersonEntity> findPeople(PaginationDto paginationDto) {
		return read("findPeople", QueryConstants.FIND_PEOPLE, WealthPersonEntity.class, paginationDto);
	}

	@Override
	public List<WealthPersonEntryEntity> findEntries(PaginationDto paginationDto) {
		return read("findEntries", QueryConstants.FIND_PERSON_ENTRIES, WealthPersonEntryEntity.class, paginationDto);
	}

	@Override
	public List<WealthBillSplitShareEntity> findShares(PaginationDto paginationDto) {
		return read("findShares", QueryConstants.FIND_BILL_SPLIT_SHARES, WealthBillSplitShareEntity.class, paginationDto);
	}

	@Override
	public List<WealthBillSplitEntity> findSplits(PaginationDto paginationDto) {
		return read("findSplits", QueryConstants.FIND_BILL_SPLITS, WealthBillSplitEntity.class, paginationDto);
	}

	@Override
	public List<WealthPersonRollupEntity> findRollups(PaginationDto paginationDto) {
		return read("findRollups", QueryConstants.FIND_PERSON_ROLLUPS, WealthPersonRollupEntity.class, paginationDto);
	}

	@Override
	public List<WealthLendRequestEntity> findPendingRequests(PaginationDto paginationDto) {
		return read("findPendingRequests", QueryConstants.FIND_PENDING_LEND_REQUESTS, WealthLendRequestEntity.class, paginationDto);
	}

	@Override
	public List<WealthPersonAliasEntity> findAliases(PaginationDto paginationDto) {
		return read("findAliases", QueryConstants.FIND_PERSON_ALIASES, WealthPersonAliasEntity.class, paginationDto);
	}

	@Override
	public List<WealthLendCommentEntity> findComments(PaginationDto paginationDto) {
		return read("findComments", QueryConstants.FIND_LEND_COMMENTS, WealthLendCommentEntity.class, paginationDto);
	}

	@Override
	public List<WealthTransactionEntity> findUnlinkedTransfers(PaginationDto paginationDto) {
		return read("findUnlinkedTransfers", QueryConstants.FIND_UNLINKED_TRANSFERS, WealthTransactionEntity.class, paginationDto);
	}
}
