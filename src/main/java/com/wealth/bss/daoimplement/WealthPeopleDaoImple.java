package com.wealth.bss.daoimplement;

import java.util.ArrayList;
import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

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
import com.wealth.common.generic.BaseError;
import com.wealth.common.generic.KarthiException;
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

	@Override
	public WealthTransactionEntity findTransactionForSplit(String userId, String transactionId) {
		List<WealthTransactionEntity> rows = jdbcTemplate.query(QueryConstants.FIND_SPLIT_TRANSACTION, new BeanPropertyRowMapper<>(WealthTransactionEntity.class), userId, transactionId);
		return rows.isEmpty() ? null : rows.get(0);
	}

	@Override
	public boolean transactionLinkedToPerson(String userId, String transactionId) {
		Integer count = jdbcTemplate.queryForObject(QueryConstants.COUNT_TRANSACTION_PERSON_ENTRIES, Integer.class, userId, transactionId);
		return count != null && count > 0;
	}

	@Override
	@Transactional
	public String createSplit(String userId, WealthBillSplitEntity split, List<WealthPersonEntity> people, List<WealthBillSplitShareEntity> shares, WealthBillSplitShareEntity mine) {
		String methodName = "createSplit";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		setActor(split);
		List<String> ids = new ArrayList<>();
		for (WealthPersonEntity person : people) {
			String id = resolvePerson(userId, person);
			if (ids.contains(id)) {
				throw new KarthiException(BaseError.SPLIT_PERSON_INVALID);
			}
			ids.add(id);
		}
		String splitId;
		try {
			splitId = jdbcTemplate.queryForObject(QueryConstants.INSERT_BILL_SPLIT, String.class, userId, split.getTransactionId(), split.getTitle(), split.getOn(), split.getTotal(), split.getTax(),
					split.getMode(), split.getNote());
		} catch (DuplicateKeyException e) {
			throw new KarthiException(BaseError.SPLIT_ALREADY_EXISTS);
		}
		for (int i = 0; i < shares.size(); i++) {
			WealthBillSplitShareEntity share = shares.get(i);
			jdbcTemplate.update(QueryConstants.INSERT_BILL_SPLIT_SHARE, splitId, ids.get(i), share.getBase(), share.getTax(), share.getAmount());
		}
		if (mine != null) {
			jdbcTemplate.update(QueryConstants.INSERT_BILL_SPLIT_SHARE, splitId, null, mine.getBase(), mine.getTax(), mine.getAmount());
		}
		if (!ids.isEmpty()) {
			jdbcTemplate.update(QueryConstants.REOPEN_SETTLED_PEOPLE, userId, String.join(",", ids));
		}
		LOGGER.info(WealthServiceConstants.EXITING + methodName + " | people=" + ids.size());
		return splitId;
	}

	@Override
	@Transactional
	public boolean cancelSplit(String userId, String splitId) {
		String methodName = "cancelSplit";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		setActor(new WealthBillSplitEntity());
		int changed = jdbcTemplate.update(QueryConstants.CANCEL_BILL_SPLIT, splitId, userId);
		LOGGER.info(WealthServiceConstants.EXITING + methodName + " | changed=" + changed);
		return changed == 1;
	}

	// A person by id (must be the caller's and not removed) or by name: the one already there, a removed one brought back, or a new one.
	private String resolvePerson(String userId, WealthPersonEntity person) {
		if (person.getId() != null) {
			List<WealthPersonEntity> found = jdbcTemplate.query(QueryConstants.FIND_PERSON_BY_ID, new BeanPropertyRowMapper<>(WealthPersonEntity.class), userId, person.getId());
			if (found.isEmpty() || WealthServiceConstants.PEOPLE_STATUS_REMOVED.equals(found.get(0).getStatus())) {
				throw new KarthiException(BaseError.SPLIT_PERSON_INVALID);
			}
			return found.get(0).getId();
		}
		List<WealthPersonEntity> found = jdbcTemplate.query(QueryConstants.FIND_PERSON_BY_NAME, new BeanPropertyRowMapper<>(WealthPersonEntity.class), userId, person.getName());
		if (found.isEmpty()) {
			return jdbcTemplate.queryForObject(QueryConstants.INSERT_PERSON, String.class, userId, person.getName());
		}
		WealthPersonEntity existing = found.get(0);
		if (WealthServiceConstants.PEOPLE_STATUS_REMOVED.equals(existing.getStatus())) {
			jdbcTemplate.update(QueryConstants.REOPEN_REMOVED_PERSON, userId, existing.getId());
		}
		return existing.getId();
	}
}
