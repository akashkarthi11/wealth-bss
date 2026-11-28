package com.wealth.bss.daoimplement;

import java.util.List;

import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.wealth.bss.common.model.dao.IfWealthTransactionDao;
import com.wealth.bss.common.model.dto.PaginationDto;
import com.wealth.bss.common.model.entity.WealthTransactionEntity;
import com.wealth.bss.constants.WealthServiceConstants;
import com.wealth.bss.query.QueryConstants;
import com.wealth.common.logger.KarthiLogger;

@Repository
public class WealthTransactionDaoImple extends WealthBaseDaoImple implements IfWealthTransactionDao {

	private static final KarthiLogger LOGGER = new KarthiLogger(WealthTransactionDaoImple.class);

	public WealthTransactionDaoImple(JdbcTemplate jdbcTemplate) {
		super(jdbcTemplate);
	}

	private static String text(String value) {
		return value == null ? "" : value;
	}

	// The search words are matched as text: the characters LIKE treats as wildcards are escaped.
	private static String like(String searchText) {
		return text(searchText).isEmpty() ? "" : "%" + searchText.toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
	}

	@Override
	public List<WealthTransactionEntity> findPage(PaginationDto page) {
		String methodName = "findPage";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		String q = text(page.getSearchText());
		List<WealthTransactionEntity> rows = jdbcTemplate.query(QueryConstants.FIND_TRANSACTION_PAGE, new BeanPropertyRowMapper<>(WealthTransactionEntity.class), page.getUserId(),
				text(page.getKind()), text(page.getKind()), text(page.getAccountId()), text(page.getAccountId()), text(page.getFromDay()), text(page.getFromDay()), text(page.getToDay()),
				text(page.getToDay()), q, like(q), text(page.getCursorDay()), text(page.getCursorDay()), text(page.getCursorCreated()), text(page.getCursorId()), page.getPageSize());
		LOGGER.info(WealthServiceConstants.EXITING + methodName + " | rows=" + rows.size());
		return rows;
	}

	@Override
	public String findAccountIdByName(String userId, String name) {
		List<String> ids = jdbcTemplate.queryForList(QueryConstants.FIND_ACCOUNT_ID_BY_NAME, String.class, userId, name, name, name);
		return ids.isEmpty() ? null : ids.get(0);
	}

	@Override
	@Transactional
	public WealthTransactionEntity insert(WealthTransactionEntity entity) {
		String methodName = "insert";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		setActor(entity);
		List<WealthTransactionEntity> rows = jdbcTemplate.query(QueryConstants.INSERT_TRANSACTION, new BeanPropertyRowMapper<>(WealthTransactionEntity.class), entity.getUserId(),
				entity.getKind(), entity.getCategory(), entity.getDescription(), entity.getAmount(), entity.getAccountId(), entity.getOccurredOn(), entity.getExternalRef(), entity.getReviewStatus());
		LOGGER.info(WealthServiceConstants.EXITING + methodName + " | inserted=" + rows.size());
		return rows.isEmpty() ? null : rows.get(0);
	}

	@Override
	public List<WealthTransactionEntity> findStatementMatches(String userId, String accountId, java.math.BigDecimal amount, String direction, String day) {
		return jdbcTemplate.query(QueryConstants.FIND_STATEMENT_MATCHES, new BeanPropertyRowMapper<>(WealthTransactionEntity.class), userId, accountId, accountId, amount, day, day, direction);
	}

	@Override
	public List<WealthTransactionEntity> findByIds(String userId, List<String> ids) {
		return ids.isEmpty() ? List.of() : jdbcTemplate.query(QueryConstants.FIND_TRANSACTIONS_BY_IDS, new BeanPropertyRowMapper<>(WealthTransactionEntity.class), userId, String.join(",", ids));
	}

	@Override
	public boolean referenceExists(String userId, String reference) {
		Integer count = jdbcTemplate.queryForObject(QueryConstants.COUNT_TRANSACTION_REFERENCE, Integer.class, userId, reference);
		return count != null && count > 0;
	}

	@Override
	public boolean accountBelongsTo(String userId, String accountId) {
		Integer count = jdbcTemplate.queryForObject(QueryConstants.COUNT_USER_ACCOUNT, Integer.class, userId, accountId);
		return count != null && count > 0;
	}

	@Override
	public boolean assignAccountIfMissing(String userId, String reference, String accountId) {
		return jdbcTemplate.update(QueryConstants.ASSIGN_TRANSACTION_ACCOUNT, accountId, userId, reference) > 0;
	}

	@Override
	public List<String> findAccountIdsByDigits(String userId, String digits) {
		return jdbcTemplate.queryForList(QueryConstants.FIND_ACCOUNT_IDS_BY_DIGITS, String.class, userId, digits);
	}

	@Override
	@Transactional
	public boolean delete(String userId, String id) {
		String methodName = "delete";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		WealthTransactionEntity who = new WealthTransactionEntity();
		setActor(who);
		jdbcTemplate.update(QueryConstants.DELETE_TRANSACTION_REWARDS, userId, userId, id);
		jdbcTemplate.update(QueryConstants.CANCEL_TRANSACTION_OFFER_USES, userId, id);
		int removed = jdbcTemplate.update(QueryConstants.DELETE_TRANSACTION, id, userId);
		LOGGER.info(WealthServiceConstants.EXITING + methodName + " | removed=" + removed);
		return removed == 1;
	}

	@Override
	public List<com.wealth.common.model.dto.transaction.TransactionSummaryItemDto> summaryBy(PaginationDto page, String dimension) {
		String methodName = "summaryBy";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName + " | " + dimension);
		String query = switch (dimension) {
		case "category" -> QueryConstants.SUMMARY_BY_CATEGORY;
		case "account" -> QueryConstants.SUMMARY_BY_ACCOUNT;
		case "kind" -> QueryConstants.SUMMARY_BY_KIND;
		case "month" -> QueryConstants.SUMMARY_BY_MONTH;
		default -> QueryConstants.SUMMARY_TOTAL;
		};
		String q = text(page.getSearchText());
		List<com.wealth.common.model.dto.transaction.TransactionSummaryItemDto> rows = jdbcTemplate.query(query,
				new BeanPropertyRowMapper<>(com.wealth.common.model.dto.transaction.TransactionSummaryItemDto.class), page.getUserId(), text(page.getKind()), text(page.getKind()),
				text(page.getAccountId()), text(page.getAccountId()), text(page.getFromDay()), text(page.getFromDay()), text(page.getToDay()), text(page.getToDay()), q, like(q));
		LOGGER.info(WealthServiceConstants.EXITING + methodName + " | rows=" + rows.size());
		return rows;
	}

	@Override
	public List<String> findAccountIdsByLastDigits(String userId, String lastDigits, String bank) {
		return jdbcTemplate.queryForList(QueryConstants.FIND_ACCOUNT_IDS_BY_LAST_DIGITS, String.class, userId, lastDigits, bank);
	}

	@Override
	public List<WealthTransactionEntity> findPendingFromGmail(String userId) {
		String methodName = "findPendingFromGmail";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		List<WealthTransactionEntity> rows = jdbcTemplate.query(QueryConstants.FIND_GMAIL_PENDING_TRANSACTIONS, new BeanPropertyRowMapper<>(WealthTransactionEntity.class), userId);
		LOGGER.info(WealthServiceConstants.EXITING + methodName + " | rows=" + rows.size());
		return rows;
	}

	@Override
	@Transactional
	public boolean setGmailReviewStatus(String userId, String id, String status) {
		String methodName = "setGmailReviewStatus";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		setActor(new WealthTransactionEntity());
		int changed = jdbcTemplate.update(QueryConstants.UPDATE_GMAIL_TRANSACTION_REVIEW, status, userId, id);
		LOGGER.info(WealthServiceConstants.EXITING + methodName + " | changed=" + changed);
		return changed == 1;
	}
}
