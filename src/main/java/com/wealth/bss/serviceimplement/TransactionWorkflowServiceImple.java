package com.wealth.bss.serviceimplement;

import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.wealth.bss.common.model.dao.IfWealthAccountDao;
import com.wealth.bss.common.model.dao.IfWealthTransactionDao;
import com.wealth.bss.common.model.dao.IfWealthTransactionWorkflowDao;
import com.wealth.bss.common.model.dto.PaginationDto;
import com.wealth.bss.common.model.entity.WealthAccountEntity;
import com.wealth.bss.common.model.entity.WealthTransactionEntity;
import com.wealth.bss.common.model.entity.WealthTransactionWorkflowEntity;
import com.wealth.bss.constants.WealthServiceConstants;
import com.wealth.bss.nexus.service.IfNexusReadService;
import com.wealth.bss.serviceinterface.IfBankImportService;
import com.wealth.bss.serviceinterface.IfTransactionWorkflowService;
import com.wealth.common.generic.BaseError;
import com.wealth.common.generic.KarthiException;
import com.wealth.common.logger.KarthiLogger;
import com.wealth.common.model.dto.transaction.BankImportResultDto;
import com.wealth.common.model.dto.transaction.BankLineDto;
import com.wealth.common.model.dto.transaction.TransactionDto;
import com.wealth.common.model.dto.transaction.WorkflowDecisionRequestDto;
import com.wealth.common.model.dto.transaction.WorkflowItemDto;
import com.wealth.common.utils.generic.RequestHeaderUtil;

// One place that decides where a bank transaction goes: no doubt = the transactions table at once; a repeat = nowhere; a duplicate, fake or unsure line = the workflow table until the user
// decides. Nothing here writes an account balance (rules 1).
@Service
public class TransactionWorkflowServiceImple implements IfBankImportService, IfTransactionWorkflowService {

	private static final KarthiLogger LOGGER = new KarthiLogger(TransactionWorkflowServiceImple.class);
	private static final Pattern ID_SHAPE = Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
	private static final Pattern DAY = Pattern.compile(WealthServiceConstants.DAY_PATTERN);

	private final IfWealthTransactionDao transactionDao;
	private final IfWealthTransactionWorkflowDao workflowDao;
	private final IfWealthAccountDao accountDao;
	private final IfNexusReadService nexusReadService;

	public TransactionWorkflowServiceImple(IfWealthTransactionDao transactionDao, IfWealthTransactionWorkflowDao workflowDao, IfWealthAccountDao accountDao,
			IfNexusReadService nexusReadService) {
		this.transactionDao = transactionDao;
		this.workflowDao = workflowDao;
		this.accountDao = accountDao;
		this.nexusReadService = nexusReadService;
	}

	@Override
	public BankImportResultDto importLines(String source, List<BankLineDto> lines) {
		String methodName = "importLines";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName + " | lines=" + lines.size());
		String userId = RequestHeaderUtil.getUserId();
		BankImportResultDto result = new BankImportResultDto();
		for (BankLineDto line : lines) {
			check(line);
			String digits = line.getAccountDigits() == null ? "" : line.getAccountDigits().trim();
			String accountId = accountOf(userId, line, digits);
			if (transactionDao.referenceExists(userId, line.getRef()) || workflowDao.exists(userId, line.getRef())) {
				if (accountId != null && transactionDao.assignAccountIfMissing(userId, line.getRef(), accountId)) {
					result.setMapped(result.getMapped() + 1);
				}
				result.setRepeated(result.getRepeated() + 1);
				continue;
			}
			String direction = line.isDebit() ? WealthServiceConstants.DIRECTION_DEBIT : WealthServiceConstants.DIRECTION_CREDIT;
			List<WealthTransactionEntity> matches = transactionDao.findStatementMatches(userId, accountId, line.getAmount().setScale(2, RoundingMode.HALF_UP), direction, line.getDay());
			String reason = !line.isSure() ? WealthServiceConstants.WORKFLOW_PARSE_UNSURE : line.isSuspicious() ? WealthServiceConstants.WORKFLOW_SUSPICIOUS : null;
			if (reason == null && !matches.isEmpty()) {
				if (matches.size() == 1 && similar(matches.get(0).getDescription(), line.getDescription())) {
					result.setSame(result.getSame() + 1);
					continue;
				}
				reason = WealthServiceConstants.WORKFLOW_POSSIBLE_DUPLICATE;
			}
			if (reason == null) {
				if (transactionDao.insert(entityOf(userId, line, accountId, direction)) == null) {
					result.setRepeated(result.getRepeated() + 1);
				} else {
					result.setStored(result.getStored() + 1);
				}
			} else if (workflowDao.insert(doubtOf(userId, source, line, accountId, digits, direction, reason, matches))) {
				result.setReview(result.getReview() + 1);
			} else {
				result.setRepeated(result.getRepeated() + 1);
			}
		}
		if (result.getStored() > 0 || result.getMapped() > 0) {
			nexusReadService.evict(WealthServiceConstants.NEXUS_READ_TRANSACTIONS, userId);
		}
		LOGGER.info(WealthServiceConstants.EXITING + methodName + " | stored=" + result.getStored() + " same=" + result.getSame() + " review=" + result.getReview() + " repeated=" + result.getRepeated());
		return result;
	}

	// The account the user chose (it must be theirs), else the one account of that bank whose last digits match; none when there is no single match.
	private String accountOf(String userId, BankLineDto line, String digits) {
		if (!blank(line.getAccountId())) {
			if (!transactionDao.accountBelongsTo(userId, line.getAccountId())) {
				throw new KarthiException(BaseError.TRANSACTION_INVALID);
			}
			return line.getAccountId();
		}
		List<String> accounts = digits.isEmpty() ? List.of()
				: blank(line.getBank()) ? transactionDao.findAccountIdsByDigits(userId, digits) : transactionDao.findAccountIdsByLastDigits(userId, digits, line.getBank());
		return accounts.size() == 1 ? accounts.get(0) : null;
	}

	private static void check(BankLineDto line) {
		if (line == null || blank(line.getRef()) || line.getRef().length() > WealthServiceConstants.STATEMENT_REF_MAX_LENGTH || line.getDay() == null || !DAY.matcher(line.getDay()).matches()
				|| line.getAmount() == null || line.getAmount().signum() <= 0 || !validDay(line.getDay())) {
			throw new KarthiException(BaseError.TRANSACTION_INVALID);
		}
	}

	private static boolean validDay(String day) {
		try {
			return LocalDate.parse(day).toString().equals(day);
		} catch (RuntimeException e) {
			return false;
		}
	}

	private static boolean blank(String text) {
		return text == null || text.isBlank();
	}

	// Two descriptions are alike when they share enough words of three letters or more (Jaccard). With no words on either side there is nothing to compare, so they count as alike.
	static boolean similar(String first, String second) {
		Set<String> a = words(first);
		Set<String> b = words(second);
		if (a.isEmpty() || b.isEmpty()) {
			return true;
		}
		Set<String> both = new HashSet<>(a);
		both.retainAll(b);
		Set<String> either = new HashSet<>(a);
		either.addAll(b);
		return (double) both.size() / either.size() >= WealthServiceConstants.STATEMENT_SIMILARITY_MIN;
	}

	private static Set<String> words(String text) {
		Set<String> words = new HashSet<>();
		if (text != null) {
			for (String word : text.toUpperCase().split("[^A-Z0-9]+")) {
				if (word.length() >= WealthServiceConstants.STATEMENT_TOKEN_MIN_LENGTH) {
					words.add(word);
				}
			}
		}
		return words;
	}

	private static String cut(String text, int max) {
		String value = text == null ? "" : text.trim();
		return value.length() > max ? value.substring(0, max) : value;
	}

	private static WealthTransactionEntity entityOf(String userId, BankLineDto line, String accountId, String direction) {
		WealthTransactionEntity entity = new WealthTransactionEntity();
		entity.setUserId(userId);
		entity.setKind(blank(line.getKind()) ? kindOf(direction) : line.getKind().trim());
		entity.setCategory(blank(line.getCategory()) ? WealthServiceConstants.STATEMENT_CATEGORY : cut(line.getCategory(), WealthServiceConstants.TRANSACTION_CATEGORY_MAX_LENGTH));
		entity.setDescription(cut(line.getDescription(), WealthServiceConstants.TRANSACTION_DESCRIPTION_MAX_LENGTH));
		entity.setAmount(line.getAmount().setScale(2, RoundingMode.HALF_UP));
		entity.setAccountId(accountId);
		entity.setOccurredOn(line.getDay());
		entity.setExternalRef(line.getRef());
		entity.setReviewStatus(WealthServiceConstants.REVIEW_ACCEPTED);
		return entity;
	}

	private static String kindOf(String direction) {
		return WealthServiceConstants.DIRECTION_CREDIT.equals(direction) ? WealthServiceConstants.STATEMENT_KIND_INCOME : WealthServiceConstants.STATEMENT_KIND_DEBIT;
	}

	private static WealthTransactionWorkflowEntity doubtOf(String userId, String source, BankLineDto line, String accountId, String digits, String direction, String reason,
			List<WealthTransactionEntity> matches) {
		WealthTransactionWorkflowEntity entity = new WealthTransactionWorkflowEntity();
		entity.setUserId(userId);
		entity.setSourceRef(line.getRef());
		entity.setOccurredOn(line.getDay());
		entity.setDescription(cut(line.getDescription(), WealthServiceConstants.STATEMENT_DESCRIPTION_MAX_LENGTH));
		entity.setAmount(line.getAmount().setScale(2, RoundingMode.HALF_UP));
		entity.setDirection(direction);
		entity.setAccountId(accountId);
		entity.setAccountHint(digits.isEmpty() ? null : cut(digits, WealthServiceConstants.STATEMENT_DIGITS_MAX_LENGTH));
		entity.setReason(reason);
		entity.setCandidateIds(String.join(",", matches.stream().map(WealthTransactionEntity::getId).toList()));
		entity.setSource(source);
		return entity;
	}

	// ---- the Data quality screen ----

	@Override
	public List<WorkflowItemDto> list() {
		String userId = RequestHeaderUtil.getUserId();
		List<WorkflowItemDto> items = new ArrayList<>();
		for (WealthTransactionWorkflowEntity row : workflowDao.findOpen(userId)) {
			WorkflowItemDto item = new WorkflowItemDto();
			item.setId(row.getId());
			item.setSource(row.getSource());
			item.setReason(row.getReason());
			item.setOccurredOn(row.getOccurredOn());
			item.setDescription(row.getDescription());
			item.setAmount(row.getAmount());
			item.setDirection(row.getDirection());
			item.setAccountId(row.getAccountId());
			item.setAccount(accountLabel(userId, row.getAccountId()));
			for (WealthTransactionEntity candidate : transactionDao.findByIds(userId, ids(row.getCandidateIds()))) {
				TransactionDto dto = new TransactionDto();
				dto.setId(candidate.getId());
				dto.setKind(candidate.getKind());
				dto.setCategory(candidate.getCategory());
				dto.setDescription(candidate.getDescription());
				dto.setAmount(candidate.getAmount());
				dto.setAccountId(candidate.getAccountId());
				dto.setAccount(candidate.getAccount());
				dto.setOccurredOn(candidate.getOccurredOn());
				item.getCandidates().add(dto);
			}
			items.add(item);
		}
		return items;
	}

	private String accountLabel(String userId, String accountId) {
		if (accountId == null) {
			return WealthServiceConstants.TRANSACTION_UNASSIGNED;
		}
		PaginationDto lookup = new PaginationDto();
		lookup.setUserId(userId);
		lookup.setId(accountId);
		WealthAccountEntity account = accountDao.findById(lookup);
		return account == null ? WealthServiceConstants.TRANSACTION_UNASSIGNED : (account.getAccountName() + (account.getMaskedIdentifier() == null ? "" : " " + account.getMaskedIdentifier())).trim();
	}

	private static List<String> ids(String csv) {
		return blank(csv) ? List.of() : List.of(csv.split(","));
	}

	@Override
	public Boolean decide(WorkflowDecisionRequestDto request) {
		String methodName = "decide";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		String userId = RequestHeaderUtil.getUserId();
		if (request == null || request.getId() == null || !ID_SHAPE.matcher(request.getId()).matches()) {
			throw new KarthiException(BaseError.INVALID_REQUEST);
		}
		String decision = request.getDecision() == null ? "" : request.getDecision().trim();
		WealthTransactionWorkflowEntity row = workflowDao.find(userId, request.getId());
		if (row == null || !WealthServiceConstants.WORKFLOW_OPEN.equals(row.getStatus())) {
			throw new KarthiException(BaseError.WORKFLOW_NOT_FOUND);
		}
		String status;
		switch (decision) {
		case WealthServiceConstants.DECISION_MERGE -> {
			if (ids(row.getCandidateIds()).isEmpty()) {
				throw new KarthiException(BaseError.WORKFLOW_DECISION_INVALID);
			}
			status = WealthServiceConstants.WORKFLOW_MERGED;
		}
		case WealthServiceConstants.DECISION_ACCEPT -> {
			WealthTransactionEntity entity = new WealthTransactionEntity();
			entity.setUserId(userId);
			entity.setKind(kindOf(row.getDirection()));
			entity.setCategory(WealthServiceConstants.WORKFLOW_SOURCE_ALERT.equals(row.getSource()) ? WealthServiceConstants.GMAIL_CATEGORY : WealthServiceConstants.STATEMENT_CATEGORY);
			entity.setDescription(cut(row.getDescription(), WealthServiceConstants.TRANSACTION_DESCRIPTION_MAX_LENGTH));
			entity.setAmount(row.getAmount());
			entity.setAccountId(row.getAccountId());
			entity.setOccurredOn(row.getOccurredOn());
			entity.setExternalRef(row.getSourceRef());
			entity.setReviewStatus(WealthServiceConstants.REVIEW_ACCEPTED);
			transactionDao.insert(entity);
			status = WealthServiceConstants.WORKFLOW_ACCEPTED;
		}
		case WealthServiceConstants.DECISION_DISMISS -> status = WealthServiceConstants.WORKFLOW_DISMISSED;
		default -> throw new KarthiException(BaseError.WORKFLOW_DECISION_INVALID);
		}
		if (!workflowDao.decide(userId, row.getId(), status)) {
			throw new KarthiException(BaseError.WORKFLOW_NOT_FOUND);
		}
		if (WealthServiceConstants.WORKFLOW_ACCEPTED.equals(status)) {
			nexusReadService.evict(WealthServiceConstants.NEXUS_READ_TRANSACTIONS, userId);
		}
		LOGGER.info(WealthServiceConstants.EXITING + methodName + " | " + status);
		return true;
	}
}
