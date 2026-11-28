package com.wealth.bss.daoimplement;

import java.math.BigDecimal;
import java.sql.Timestamp;
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

	@Override
	public List<WealthTransactionEntity> findTransfersForEntries(String userId, List<String> transactionIds) {
		return jdbcTemplate.query(QueryConstants.FIND_TRANSFERS_FOR_ENTRIES, new BeanPropertyRowMapper<>(WealthTransactionEntity.class), userId, String.join(",", transactionIds));
	}

	@Override
	public WealthPersonEntity findPerson(String userId, String personId) {
		List<WealthPersonEntity> rows = jdbcTemplate.query(QueryConstants.FIND_PERSON_BY_ID, new BeanPropertyRowMapper<>(WealthPersonEntity.class), userId, personId);
		return rows.isEmpty() ? null : rows.get(0);
	}

	@Override
	@Transactional
	public String addEntries(String userId, WealthPersonEntity person, List<WealthPersonEntryEntity> entries, List<String> aliases) {
		String methodName = "addEntries";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		setActor(new WealthPersonEntryEntity());
		String personId = resolvePerson(userId, person);
		boolean lent = false;
		for (WealthPersonEntryEntity entry : entries) {
			int stored = jdbcTemplate.update(QueryConstants.INSERT_PERSON_ENTRY, personId, entry.getDirection(), entry.getAmount(), entry.getOn(), entry.getTransactionId(), entry.getNote());
			if (stored != 1) {
				throw new KarthiException(BaseError.ENTRY_TRANSACTION_LINKED);
			}
			lent = lent || WealthServiceConstants.PEOPLE_DIRECTION_OUT.equals(entry.getDirection());
		}
		if (lent) {
			jdbcTemplate.update(QueryConstants.REOPEN_SETTLED_PEOPLE, userId, personId);
		}
		for (String alias : aliases) {
			jdbcTemplate.update(QueryConstants.INSERT_PERSON_ALIAS, userId, personId, alias, alias.toLowerCase());
		}
		LOGGER.info(WealthServiceConstants.EXITING + methodName + " | entries=" + entries.size());
		return personId;
	}

	@Override
	public WealthBillSplitShareEntity findShareToPay(String userId, String splitId, String personId) {
		List<WealthBillSplitShareEntity> rows = jdbcTemplate.query(QueryConstants.FIND_SHARE_TO_PAY, new BeanPropertyRowMapper<>(WealthBillSplitShareEntity.class), userId, splitId, personId);
		return rows.isEmpty() ? null : rows.get(0);
	}

	@Override
	public boolean sharePaid(String personId, String splitId) {
		Integer count = jdbcTemplate.queryForObject(QueryConstants.COUNT_SHARE_PAID_ENTRIES, Integer.class, personId, splitId);
		return count != null && count > 0;
	}

	@Override
	@Transactional
	public boolean addSharePaid(String userId, String personId, String splitId, BigDecimal amount, String on, String note) {
		String methodName = "addSharePaid";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		setActor(new WealthPersonEntryEntity());
		boolean stored = jdbcTemplate.update(QueryConstants.INSERT_SHARE_PAID, personId, amount, on, splitId, note) == 1;
		LOGGER.info(WealthServiceConstants.EXITING + methodName + " | stored=" + stored);
		return stored;
	}

	@Override
	@Transactional
	public int roundOff(String userId, WealthPersonRollupEntity rollup, String entryNote) {
		String methodName = "roundOff";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		setActor(rollup);
		Integer closed = jdbcTemplate.queryForObject(QueryConstants.ROUND_OFF_PERSON, (rs, row) -> {
			rollup.setId(rs.getString("ID"));
			return rs.getInt("CLOSED");
		}, rollup.getPersonId(), rollup.getOn(), rollup.getTotalOwed(), rollup.getAlreadyBack(), rollup.getNewAmount(), rollup.getClosedItems(), rollup.getNote(), entryNote, rollup.getPersonId(), userId);
		if (rollup.getId() == null) {
			throw new KarthiException(BaseError.PERSON_NOT_FOUND);
		}
		LOGGER.info(WealthServiceConstants.EXITING + methodName + " | closed=" + closed);
		return closed == null ? 0 : closed;
	}

	@Override
	public WealthPersonEntryEntity findEntryForStatus(String userId, String entryId) {
		List<WealthPersonEntryEntity> rows = jdbcTemplate.query(QueryConstants.FIND_ENTRY_FOR_STATUS, new BeanPropertyRowMapper<>(WealthPersonEntryEntity.class), userId, entryId);
		return rows.isEmpty() ? null : rows.get(0);
	}

	@Override
	@Transactional
	public boolean setEntryStatus(String userId, String entryId, String status) {
		String methodName = "setEntryStatus";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		setActor(new WealthPersonEntryEntity());
		boolean changed = jdbcTemplate.update(QueryConstants.UPDATE_ENTRY_STATUS, status, entryId, userId) == 1;
		LOGGER.info(WealthServiceConstants.EXITING + methodName + " | changed=" + changed);
		return changed;
	}

	@Override
	public WealthBillSplitShareEntity findShareForStatus(String userId, String splitId, String personId) {
		List<WealthBillSplitShareEntity> rows = jdbcTemplate.query(QueryConstants.FIND_SHARE_FOR_STATUS, new BeanPropertyRowMapper<>(WealthBillSplitShareEntity.class), userId, splitId, personId);
		return rows.isEmpty() ? null : rows.get(0);
	}

	@Override
	@Transactional
	public boolean setShareStatus(String userId, String shareId, String status) {
		String methodName = "setShareStatus";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		setActor(new WealthBillSplitShareEntity());
		boolean changed = jdbcTemplate.update(QueryConstants.UPDATE_SHARE_STATUS, status, shareId, userId) == 1;
		LOGGER.info(WealthServiceConstants.EXITING + methodName + " | changed=" + changed);
		return changed;
	}

	@Override
	@Transactional
	public String insertPerson(String userId, WealthPersonEntity person) {
		String methodName = "insertPerson";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		setActor(person);
		List<String> ids = jdbcTemplate.queryForList(QueryConstants.INSERT_PERSON_FULL, String.class, userId, person.getName(), person.getKind(), person.getDueOn(), person.getAnnualRate(), person.getStatus(),
				person.getNote(), person.isHidden());
		LOGGER.info(WealthServiceConstants.EXITING + methodName + " | added=" + !ids.isEmpty());
		return ids.isEmpty() ? null : ids.get(0);
	}

	@Override
	@Transactional
	public boolean updatePerson(String userId, String personId, java.util.Set<String> changed, WealthPersonEntity values, String keepAlias) {
		String methodName = "updatePerson";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		setActor(values);
		boolean updated;
		try {
			updated = jdbcTemplate.update(QueryConstants.UPDATE_PERSON, changed.contains(WealthServiceConstants.PEOPLE_FIELD_NAME), values.getName(), changed.contains(WealthServiceConstants.PEOPLE_FIELD_KIND),
					values.getKind(), changed.contains(WealthServiceConstants.PEOPLE_FIELD_DUE_ON), values.getDueOn(), changed.contains(WealthServiceConstants.PEOPLE_FIELD_RATE), values.getAnnualRate(),
					changed.contains(WealthServiceConstants.PEOPLE_FIELD_STATUS), values.getStatus(), changed.contains(WealthServiceConstants.PEOPLE_FIELD_NOTE), values.getNote(),
					changed.contains(WealthServiceConstants.PEOPLE_FIELD_HIDDEN), values.isHidden(), personId, userId) == 1;
		} catch (DuplicateKeyException e) {
			throw new KarthiException(BaseError.PERSON_NAME_TAKEN);
		}
		if (updated && keepAlias != null && !keepAlias.isEmpty()) {
			jdbcTemplate.update(QueryConstants.INSERT_PERSON_ALIAS, userId, personId, keepAlias, keepAlias.toLowerCase());
		}
		LOGGER.info(WealthServiceConstants.EXITING + methodName + " | updated=" + updated);
		return updated;
	}

	@Override
	public int countPersonRecords(String personId) {
		Integer count = jdbcTemplate.queryForObject(QueryConstants.COUNT_PERSON_RECORDS, Integer.class, personId, personId);
		return count == null ? 0 : count;
	}

	@Override
	@Transactional
	public boolean setShareToken(String userId, String personId, String hash) {
		String methodName = "setShareToken";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		setActor(new WealthPersonEntity());
		boolean changed = jdbcTemplate.update(QueryConstants.SET_SHARE_TOKEN, hash, personId, userId) == 1;
		LOGGER.info(WealthServiceConstants.EXITING + methodName + " | changed=" + changed);
		return changed;
	}

	@Override
	@Transactional
	public boolean clearShareToken(String userId, String personId) {
		String methodName = "clearShareToken";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		setActor(new WealthPersonEntity());
		boolean changed = jdbcTemplate.update(QueryConstants.CLEAR_SHARE_TOKEN, personId, userId) == 1;
		LOGGER.info(WealthServiceConstants.EXITING + methodName + " | changed=" + changed);
		return changed;
	}

	@Override
	public WealthPersonEntity findPersonByShareHash(String hash) {
		List<WealthPersonEntity> rows = jdbcTemplate.query(QueryConstants.FIND_PERSON_BY_SHARE_HASH, new BeanPropertyRowMapper<>(WealthPersonEntity.class), hash);
		return rows.isEmpty() ? null : rows.get(0);
	}

	@Override
	public String findMergeClash(String intoId, String fromId) {
		List<String> titles = jdbcTemplate.queryForList(QueryConstants.FIND_MERGE_CLASH, String.class, intoId, fromId);
		return titles.isEmpty() ? null : titles.get(0);
	}

	@Override
	@Transactional
	public void mergePeople(String userId, String fromId, String intoId, String keepAlias) {
		String methodName = "mergePeople";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		setActor(new WealthPersonEntity());
		if (findPerson(userId, fromId) == null || findPerson(userId, intoId) == null) {
			throw new KarthiException(BaseError.PERSON_NOT_FOUND);
		}
		for (String move : new String[] { QueryConstants.MERGE_MOVE_ENTRIES, QueryConstants.MERGE_MOVE_SHARES, QueryConstants.MERGE_MOVE_REQUESTS, QueryConstants.MERGE_MOVE_ROLLUPS,
				QueryConstants.MERGE_MOVE_COMMENTS, QueryConstants.MERGE_MOVE_ALIASES }) {
			jdbcTemplate.update(move, intoId, fromId);
		}
		if (keepAlias != null && !keepAlias.isEmpty()) {
			jdbcTemplate.update(QueryConstants.MERGE_ALIAS_FROM_NAME, userId, intoId, keepAlias, keepAlias.toLowerCase());
		}
		jdbcTemplate.update(QueryConstants.MERGE_REMOVE_PERSON, fromId, userId);
		jdbcTemplate.update(QueryConstants.REOPEN_SETTLED_PEOPLE, userId, intoId);
		LOGGER.info(WealthServiceConstants.EXITING + methodName);
	}

	@Override
	public int countPendingRequests(String personId) {
		Integer count = jdbcTemplate.queryForObject(QueryConstants.COUNT_PENDING_LEND_REQUESTS, Integer.class, personId);
		return count == null ? 0 : count;
	}

	@Override
	@Transactional
	public void addLendRequest(String personId, BigDecimal amount, String paidOn, String method, String note) {
		String methodName = "addLendRequest";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		WealthLendRequestEntity writer = new WealthLendRequestEntity();
		writer.setActor(WealthServiceConstants.ACTOR_LEND_LINK);
		setActor(writer);
		jdbcTemplate.update(QueryConstants.INSERT_LEND_REQUEST, personId, amount, paidOn, method, note);
		LOGGER.info(WealthServiceConstants.EXITING + methodName);
	}

	@Override
	public List<WealthLendRequestEntity> findLendViewRequests(String personId) {
		return jdbcTemplate.query(QueryConstants.FIND_LEND_VIEW_REQUESTS, new BeanPropertyRowMapper<>(WealthLendRequestEntity.class), personId);
	}

	@Override
	public boolean entryOfPerson(String entryId, String personId) {
		Integer count = jdbcTemplate.queryForObject(QueryConstants.COUNT_ENTRY_OF_PERSON, Integer.class, entryId, personId);
		return count != null && count > 0;
	}

	@Override
	public boolean shareOfPerson(String shareId, String personId) {
		Integer count = jdbcTemplate.queryForObject(QueryConstants.COUNT_SHARE_OF_PERSON, Integer.class, shareId, personId);
		return count != null && count > 0;
	}

	@Override
	public int countPersonRemarksSince(String personId, Timestamp since) {
		Integer count = jdbcTemplate.queryForObject(QueryConstants.COUNT_PERSON_REMARKS_SINCE, Integer.class, personId, since);
		return count == null ? 0 : count;
	}

	@Override
	@Transactional
	public String addRemark(String personId, String entryId, String shareId, String author, String body, String actor) {
		String methodName = "addRemark";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		WealthLendCommentEntity writer = new WealthLendCommentEntity();
		writer.setActor(actor);
		setActor(writer);
		String id = jdbcTemplate.queryForObject(QueryConstants.INSERT_LEND_COMMENT, String.class, personId, entryId, shareId, author, body);
		LOGGER.info(WealthServiceConstants.EXITING + methodName);
		return id;
	}

	@Override
	public String findEntryPersonOfOwner(String userId, String entryId) {
		List<String> rows = jdbcTemplate.queryForList(QueryConstants.FIND_ENTRY_PERSON_OF_OWNER, String.class, userId, entryId);
		return rows.isEmpty() ? null : rows.get(0);
	}

	@Override
	public String findSharePersonOfOwner(String userId, String shareId) {
		List<String> rows = jdbcTemplate.queryForList(QueryConstants.FIND_SHARE_PERSON_OF_OWNER, String.class, userId, shareId);
		return rows.isEmpty() ? null : rows.get(0);
	}

	@Override
	@Transactional
	public boolean setEntryNote(String userId, String entryId, String note) {
		String methodName = "setEntryNote";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		setActor(new WealthPersonEntryEntity());
		boolean changed = jdbcTemplate.update(QueryConstants.UPDATE_ENTRY_NOTE, note, entryId, userId) == 1;
		LOGGER.info(WealthServiceConstants.EXITING + methodName + " | changed=" + changed);
		return changed;
	}

	@Override
	public WealthLendRequestEntity findRequestOfOwner(String userId, String requestId) {
		List<WealthLendRequestEntity> rows = jdbcTemplate.query(QueryConstants.FIND_LEND_REQUEST_OF_OWNER, new BeanPropertyRowMapper<>(WealthLendRequestEntity.class), userId, requestId);
		return rows.isEmpty() ? null : rows.get(0);
	}

	@Override
	@Transactional
	public boolean declineRequest(String userId, String requestId) {
		String methodName = "declineRequest";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		setActor(new WealthLendRequestEntity());
		boolean changed = jdbcTemplate.update(QueryConstants.DECLINE_LEND_REQUEST, requestId, userId) == 1;
		LOGGER.info(WealthServiceConstants.EXITING + methodName + " | changed=" + changed);
		return changed;
	}

	@Override
	@Transactional
	public boolean acceptRequest(String userId, WealthLendRequestEntity request, BigDecimal amount, String paidOn, boolean closeLend, String note) {
		String methodName = "acceptRequest";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		setActor(new WealthLendRequestEntity());
		if (jdbcTemplate.update(QueryConstants.ACCEPT_LEND_REQUEST, amount, closeLend, request.getId(), userId) != 1) {
			return false;
		}
		jdbcTemplate.update(QueryConstants.INSERT_PERSON_ENTRY, request.getPersonId(), WealthServiceConstants.PEOPLE_DIRECTION_IN, amount, paidOn, null, note);
		if (closeLend) {
			jdbcTemplate.update(QueryConstants.SETTLE_PERSON, request.getPersonId(), userId);
		}
		LOGGER.info(WealthServiceConstants.EXITING + methodName + " | closeLend=" + closeLend);
		return true;
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
