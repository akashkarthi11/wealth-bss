package com.wealth.bss.serviceimplement;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.wealth.bss.common.model.dao.IfWealthAccountDao;
import com.wealth.bss.common.model.dao.IfWealthTransactionDao;
import com.wealth.bss.common.model.dto.PaginationDto;
import com.wealth.bss.common.model.entity.WealthAccountEntity;
import com.wealth.bss.common.model.entity.WealthTransactionEntity;
import com.wealth.bss.constants.WealthServiceConstants;
import com.wealth.bss.nexus.service.IfNexusReadService;
import com.wealth.bss.serviceinterface.IfTransactionsService;
import com.wealth.common.generic.BaseError;
import com.wealth.common.generic.KarthiException;
import com.wealth.common.logger.KarthiLogger;
import com.wealth.common.model.dto.CachedValueDto;
import com.wealth.common.model.dto.IdRequestDto;
import com.wealth.common.model.dto.transaction.TransactionCreateRequestDto;
import com.wealth.common.model.dto.transaction.TransactionDto;
import com.wealth.common.model.dto.transaction.TransactionPageDto;
import com.wealth.common.model.dto.transaction.TransactionPageRequestDto;
import com.wealth.common.utils.generic.RequestHeaderUtil;

@Service
public class TransactionsServiceImple implements IfTransactionsService {

	private static final KarthiLogger LOGGER = new KarthiLogger(TransactionsServiceImple.class);
	private static final Pattern ID_SHAPE = Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
	private static final Pattern CURSOR = Pattern.compile("^\\[\"(\\d{4}-\\d{2}-\\d{2})\",\"(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{6}Z)\",\"([0-9a-fA-F-]{36})\"\\]$");

	private final IfWealthTransactionDao transactionDao;
	private final IfWealthAccountDao accountDao;
	private final IfNexusReadService nexusReadService;

	public TransactionsServiceImple(IfWealthTransactionDao transactionDao, IfWealthAccountDao accountDao, IfNexusReadService nexusReadService) {
		this.transactionDao = transactionDao;
		this.accountDao = accountDao;
		this.nexusReadService = nexusReadService;
	}

	// Only the first page without filters is cached (the nexus refreshes it when it is older than the refresh time, rules 10); later pages and filtered lists always read the table.
	@Override
	public TransactionPageDto list(TransactionPageRequestDto request) {
		String methodName = "list";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		PaginationDto page = pageOf(request);
		boolean first = page.getCursorId() == null && page.getKind() == null && page.getAccountId() == null && page.getFromDay() == null && page.getToDay() == null
				&& page.getSearchText() == null && page.getPageSize() == WealthServiceConstants.TRANSACTIONS_PAGE_DEFAULT;
		TransactionPageDto result = first ? stamp(nexusReadService.read(WealthServiceConstants.NEXUS_READ_TRANSACTIONS, TransactionPageDto.class, this::firstPage)) : read(page);
		LOGGER.info(WealthServiceConstants.EXITING + methodName + " | rows=" + result.getItems().size());
		return result;
	}

	@Override
	public com.wealth.common.model.dto.transaction.TransactionSummaryDto summary(TransactionPageRequestDto request) {
		String methodName = "summary";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		PaginationDto page = pageOf(request);
		com.wealth.common.model.dto.transaction.TransactionSummaryDto result = new com.wealth.common.model.dto.transaction.TransactionSummaryDto();
		List<com.wealth.common.model.dto.transaction.TransactionSummaryItemDto> total = transactionDao.summaryBy(page, "total");
		if (!total.isEmpty() && total.get(0).getIncoming() != null) {
			result.setIncoming(total.get(0).getIncoming().setScale(2, RoundingMode.HALF_UP));
			result.setOutgoing(total.get(0).getOutgoing().setScale(2, RoundingMode.HALF_UP));
			result.setCount(total.get(0).getCount());
		}
		result.setNet(result.getIncoming().subtract(result.getOutgoing()));
		result.setByCategory(round(transactionDao.summaryBy(page, "category")));
		result.setByAccount(round(transactionDao.summaryBy(page, "account")));
		List<com.wealth.common.model.dto.transaction.TransactionSummaryItemDto> months = new java.util.ArrayList<>(round(transactionDao.summaryBy(page, "month")));
		java.util.Collections.reverse(months);
		result.setByMonth(months);
		LOGGER.info(WealthServiceConstants.EXITING + methodName + " | count=" + result.getCount());
		return result;
	}

	private static List<com.wealth.common.model.dto.transaction.TransactionSummaryItemDto> round(List<com.wealth.common.model.dto.transaction.TransactionSummaryItemDto> items) {
		items.forEach(item -> {
			item.setIncoming(item.getIncoming().setScale(2, RoundingMode.HALF_UP));
			item.setOutgoing(item.getOutgoing().setScale(2, RoundingMode.HALF_UP));
		});
		return items;
	}

	@Override
	public TransactionPageDto refresh() {
		return stamp(nexusReadService.hardRefresh(WealthServiceConstants.NEXUS_READ_TRANSACTIONS, TransactionPageDto.class, this::firstPage));
	}

	private TransactionPageDto firstPage() {
		return read(pageOf(null));
	}

	private TransactionPageDto stamp(CachedValueDto<TransactionPageDto> cached) {
		TransactionPageDto page = cached.getData();
		page.setFetchedAt(cached.getFetchedAt());
		page.setStale(cached.isStale());
		return page;
	}

	private TransactionPageDto read(PaginationDto page) {
		List<WealthTransactionEntity> rows = transactionDao.findPage(page);
		TransactionPageDto result = new TransactionPageDto();
		for (WealthTransactionEntity row : rows) {
			result.getItems().add(toDto(row, row.getAccount()));
		}
		if (rows.size() == page.getPageSize()) {
			WealthTransactionEntity last = rows.get(rows.size() - 1);
			String key = "[\"" + last.getOccurredOn() + "\",\"" + last.getCreatedKey() + "\",\"" + last.getId() + "\"]";
			result.setNextCursor(Base64.getUrlEncoder().withoutPadding().encodeToString(key.getBytes(StandardCharsets.UTF_8)));
		}
		return result;
	}

	// The request, checked and turned into the query: a filter that was not asked for stays null.
	private PaginationDto pageOf(TransactionPageRequestDto request) {
		TransactionPageRequestDto asked = request == null ? new TransactionPageRequestDto() : request;
		int size = asked.getSize() == null ? WealthServiceConstants.TRANSACTIONS_PAGE_DEFAULT : asked.getSize();
		String kind = blankToNull(asked.getKind());
		String accountId = blankToNull(asked.getAccountId());
		String from = blankToNull(asked.getFrom());
		String to = blankToNull(asked.getTo());
		String q = blankToNull(asked.getQ());
		if (size < 1 || size > WealthServiceConstants.TRANSACTIONS_PAGE_MAX || (kind != null && !WealthServiceConstants.TRANSACTION_KINDS.contains(kind))
				|| (accountId != null && !ID_SHAPE.matcher(accountId).matches()) || !validDay(from) || !validDay(to) || (from != null && to != null && from.compareTo(to) > 0)
				|| (q != null && q.length() > WealthServiceConstants.TRANSACTION_SEARCH_MAX_LENGTH)) {
			throw new KarthiException(BaseError.INVALID_REQUEST);
		}
		PaginationDto page = new PaginationDto();
		page.setUserId(RequestHeaderUtil.getUserId());
		page.setPageSize(size);
		page.setKind(kind);
		page.setAccountId(accountId);
		page.setFromDay(from);
		page.setToDay(to);
		page.setSearchText(q);
		String cursor = blankToNull(asked.getCursor());
		if (cursor != null) {
			Matcher matcher;
			try {
				matcher = CURSOR.matcher(new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8));
			} catch (IllegalArgumentException e) {
				throw new KarthiException(BaseError.INVALID_REQUEST);
			}
			if (!matcher.matches()) {
				throw new KarthiException(BaseError.INVALID_REQUEST);
			}
			page.setCursorDay(matcher.group(1));
			page.setCursorCreated(matcher.group(2));
			page.setCursorId(matcher.group(3));
		}
		return page;
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private static boolean validDay(String day) {
		if (day == null) {
			return true;
		}
		try {
			return day.matches(WealthServiceConstants.DAY_PATTERN) && LocalDate.parse(day).toString().equals(day);
		} catch (RuntimeException e) {
			return false;
		}
	}

	@Override
	public TransactionDto create(TransactionCreateRequestDto request) {
		String methodName = "create";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		if (request == null) {
			throw new KarthiException(BaseError.INVALID_REQUEST);
		}
		String kind = request.getKind() == null ? "" : request.getKind().trim();
		String category = cut(request.getCategory(), WealthServiceConstants.TRANSACTION_CATEGORY_MAX_LENGTH);
		BigDecimal amount = request.getAmount();
		String date = request.getDate() == null ? "" : request.getDate().trim();
		if (!WealthServiceConstants.TRANSACTION_KINDS.contains(kind) || category.isEmpty() || amount == null || amount.signum() <= 0
				|| amount.doubleValue() > WealthServiceConstants.TRANSACTION_AMOUNT_MAX || !validDay(date) || date.isEmpty()) {
			throw new KarthiException(BaseError.TRANSACTION_INVALID);
		}
		String userId = RequestHeaderUtil.getUserId();
		WealthAccountEntity account = null;
		String accountId = blankToNull(request.getAccountId());
		if (accountId != null) {
			PaginationDto lookup = new PaginationDto();
			lookup.setUserId(userId);
			lookup.setId(accountId);
			account = ID_SHAPE.matcher(accountId).matches() ? accountDao.findById(lookup) : null;
			if (account == null) {
				throw new KarthiException(BaseError.TRANSACTION_ACCOUNT_INVALID);
			}
		} else if (blankToNull(request.getAccount()) != null) {
			String found = transactionDao.findAccountIdByName(userId, cut(request.getAccount(), WealthServiceConstants.ACCOUNT_TEXT_MAX_LENGTH));
			if (found != null) {
				PaginationDto lookup = new PaginationDto();
				lookup.setUserId(userId);
				lookup.setId(found);
				account = accountDao.findById(lookup);
			}
		}
		WealthTransactionEntity entity = new WealthTransactionEntity();
		entity.setUserId(userId);
		entity.setKind(kind);
		entity.setCategory(category);
		entity.setDescription(request.getDescription() == null ? "" : cut(request.getDescription(), WealthServiceConstants.TRANSACTION_DESCRIPTION_MAX_LENGTH));
		entity.setAmount(amount.setScale(2, RoundingMode.HALF_UP));
		entity.setAccountId(account == null ? null : account.getId());
		entity.setOccurredOn(date);
		String reference = cut(request.getExternalRef(), WealthServiceConstants.TRANSACTION_REFERENCE_MAX_LENGTH);
		entity.setExternalRef(reference.isEmpty() ? null : reference);
		WealthTransactionEntity saved = transactionDao.insert(entity);
		if (saved == null) {
			throw new KarthiException(BaseError.TRANSACTION_REFERENCE_EXISTS);
		}
		nexusReadService.evict(WealthServiceConstants.NEXUS_READ_TRANSACTIONS, userId);
		LOGGER.info(WealthServiceConstants.EXITING + methodName);
		return toDto(saved, label(account));
	}

	@Override
	public Boolean delete(IdRequestDto request) {
		String methodName = "delete";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		if (request == null || request.getId() == null || !ID_SHAPE.matcher(request.getId()).matches()) {
			throw new KarthiException(BaseError.INVALID_REQUEST);
		}
		String userId = RequestHeaderUtil.getUserId();
		boolean removed = transactionDao.delete(userId, request.getId());
		if (removed) {
			nexusReadService.evict(WealthServiceConstants.NEXUS_READ_TRANSACTIONS, userId);
		}
		LOGGER.info(WealthServiceConstants.EXITING + methodName + " | removed=" + removed);
		return removed;
	}

	private static String cut(String value, int max) {
		String text = value == null ? "" : value.trim();
		return text.length() > max ? text.substring(0, max) : text;
	}

	// The account as the list shows it: its name and last digits, or Unassigned.
	private static String label(WealthAccountEntity account) {
		if (account == null) {
			return WealthServiceConstants.TRANSACTION_UNASSIGNED;
		}
		String digits = account.getMaskedIdentifier() == null ? "" : " " + account.getMaskedIdentifier();
		return (account.getAccountName() + digits).trim();
	}

	private static TransactionDto toDto(WealthTransactionEntity row, String account) {
		TransactionDto dto = new TransactionDto();
		dto.setId(row.getId());
		dto.setKind(row.getKind());
		dto.setCategory(row.getCategory());
		dto.setDescription(row.getDescription());
		dto.setAmount(row.getAmount());
		dto.setAccountId(row.getAccountId());
		dto.setAccount(account);
		dto.setOccurredOn(row.getOccurredOn());
		dto.setExternalRef(row.getExternalRef());
		dto.setReviewStatus(row.getReviewStatus());
		dto.setDuplicateOf(row.getDuplicateOf());
		dto.setInactive(row.isInactive());
		return dto;
	}
}
