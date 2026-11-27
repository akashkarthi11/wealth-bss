package com.wealth.bss.serviceimplement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wealth.bss.common.model.dao.IfWealthAccountDao;
import com.wealth.bss.common.model.dao.IfWealthTransactionDao;
import com.wealth.bss.common.model.entity.WealthAccountEntity;
import com.wealth.bss.common.model.entity.WealthTransactionEntity;
import com.wealth.bss.nexus.service.IfNexusReadService;
import com.wealth.common.generic.BaseError;
import com.wealth.common.generic.KarthiException;
import com.wealth.common.model.dto.CachedValueDto;
import com.wealth.common.model.dto.IdRequestDto;
import com.wealth.common.model.dto.transaction.TransactionCreateRequestDto;
import com.wealth.common.model.dto.transaction.TransactionPageDto;
import com.wealth.common.model.dto.transaction.TransactionPageRequestDto;
import com.wealth.common.utils.generic.RequestHeaderUtil;

class TransactionsServiceImpleTest {

	private static final String USER = "2a7014a8-9cf0-4d39-bed2-c83a83eddf0c";
	private static final String ACCOUNT = "11111111-2222-3333-4444-555555555555";

	private IfWealthTransactionDao dao;
	private IfWealthAccountDao accounts;
	private IfNexusReadService nexus;
	private TransactionsServiceImple service;

	@BeforeEach
	@SuppressWarnings({ "unchecked", "rawtypes" })
	void setUp() {
		dao = mock(IfWealthTransactionDao.class);
		accounts = mock(IfWealthAccountDao.class);
		nexus = mock(IfNexusReadService.class);
		org.mockito.stubbing.Answer<Object> answer = invocation -> {
			CachedValueDto cached = new CachedValueDto();
			cached.setData(((java.util.function.Supplier) invocation.getArgument(2)).get());
			cached.setFetchedAt(Timestamp.valueOf("2026-11-27 10:00:00"));
			return cached;
		};
		when(nexus.read(any(), any(Class.class), any(java.util.function.Supplier.class))).thenAnswer(answer);
		when(nexus.hardRefresh(any(), any(Class.class), any(java.util.function.Supplier.class))).thenAnswer(answer);
		service = new TransactionsServiceImple(dao, accounts, nexus);
		RequestHeaderUtil.setUserId(USER);
	}

	@AfterEach
	void tearDown() {
		RequestHeaderUtil.clear();
	}

	private WealthTransactionEntity row(String id, String on, String created) {
		WealthTransactionEntity row = new WealthTransactionEntity();
		row.setId(id);
		row.setKind("expense");
		row.setCategory("Food");
		row.setDescription("Dinner");
		row.setAmount(new BigDecimal("450.00"));
		row.setAccount("HDFC Salary 4521");
		row.setOccurredOn(on);
		row.setCreatedKey(created);
		row.setReviewStatus("pending");
		return row;
	}

	private static com.wealth.common.model.dto.transaction.TransactionSummaryItemDto item(String label, String in, String out, int count) {
		com.wealth.common.model.dto.transaction.TransactionSummaryItemDto item = new com.wealth.common.model.dto.transaction.TransactionSummaryItemDto();
		item.setLabel(label);
		item.setIncoming(new BigDecimal(in));
		item.setOutgoing(new BigDecimal(out));
		item.setCount(count);
		return item;
	}

	@Test
	void theDashboardGivesTotalsNetAndTheSplitsWithMonthsOldestFirstForTheSameFilters() {
		when(dao.summaryBy(any(), org.mockito.ArgumentMatchers.eq("total"))).thenReturn(new ArrayList<>(List.of(item("total", "10000", "1200.5", 4))));
		when(dao.summaryBy(any(), org.mockito.ArgumentMatchers.eq("category"))).thenReturn(new ArrayList<>(List.of(item("Travel", "0", "700", 1), item("Food", "0", "500.5", 2))));
		when(dao.summaryBy(any(), org.mockito.ArgumentMatchers.eq("account"))).thenReturn(new ArrayList<>(List.of(item("Savings xx3333", "10000", "1000", 3))));
		when(dao.summaryBy(any(), org.mockito.ArgumentMatchers.eq("month"))).thenReturn(new ArrayList<>(List.of(item("2026-11", "0", "900", 2), item("2026-10", "10000", "300", 2))));
		TransactionPageRequestDto request = new TransactionPageRequestDto();
		request.setKind("expense");
		request.setFrom("2026-10-01");
		request.setQ("swiggy");

		var summary = service.summary(request);

		assertEquals(new BigDecimal("10000.00"), summary.getIncoming());
		assertEquals(new BigDecimal("1200.50"), summary.getOutgoing());
		assertEquals(new BigDecimal("8799.50"), summary.getNet());
		assertEquals(4, summary.getCount());
		assertEquals(List.of("Travel", "Food"), summary.getByCategory().stream().map(i -> i.getLabel()).toList());
		assertEquals(List.of("2026-10", "2026-11"), summary.getByMonth().stream().map(i -> i.getLabel()).toList(), "months are oldest first");
		verify(dao).summaryBy(argThat(page -> USER.equals(page.getUserId()) && "expense".equals(page.getKind()) && "2026-10-01".equals(page.getFromDay()) && "swiggy".equals(page.getSearchText())),
				org.mockito.ArgumentMatchers.eq("total"));
	}

	@Test
	void anEmptySelectionGivesZerosAndABadFilterIsRefused() {
		when(dao.summaryBy(any(), any())).thenReturn(new ArrayList<>());
		var empty = service.summary(null);
		assertEquals(BigDecimal.ZERO, empty.getNet());
		assertEquals(0, empty.getCount());
		assertTrue(empty.getByMonth().isEmpty());

		TransactionPageRequestDto bad = new TransactionPageRequestDto();
		bad.setKind("nonsense");
		assertEquals(BaseError.INVALID_REQUEST, assertThrows(KarthiException.class, () -> service.summary(bad)).getMessage());
	}

	@Test
	void theFirstPageWithoutFiltersIsReadThroughTheCacheAtTheDefaultSize() {
		when(dao.findPage(any())).thenReturn(List.of(row("a1111111-2222-3333-4444-555555555555", "2026-11-26", "2026-11-26T10:00:00.123456Z")));

		TransactionPageDto page = service.list(null);

		verify(dao).findPage(argThat(p -> USER.equals(p.getUserId()) && p.getPageSize() == 50 && p.getKind() == null && p.getCursorId() == null && p.getSearchText() == null));
		assertEquals(1, page.getItems().size());
		assertEquals("HDFC Salary 4521", page.getItems().get(0).getAccount());
		assertEquals(Timestamp.valueOf("2026-11-27 10:00:00"), page.getFetchedAt());
		assertNull(page.getNextCursor(), "a short page is the last one");
		verify(nexus).read(any(), any(Class.class), any(java.util.function.Supplier.class));
	}

	@Test
	void aFullPageGivesACursorThatTheNextCallContinuesAfterAndFiltersSkipTheCache() {
		List<WealthTransactionEntity> rows = new ArrayList<>();
		for (int i = 0; i < 2; i++) {
			rows.add(row("a111111" + i + "-2222-3333-4444-555555555555", "2026-11-2" + (6 - i), "2026-11-2" + (6 - i) + "T10:00:00.123456Z"));
		}
		when(dao.findPage(any())).thenReturn(rows);
		TransactionPageRequestDto first = new TransactionPageRequestDto();
		first.setSize(2);
		first.setKind("expense");
		first.setQ("dinner");

		TransactionPageDto page = service.list(first);

		verify(nexus, never()).read(any(), any(Class.class), any(java.util.function.Supplier.class));
		assertNotNull(page.getNextCursor());
		assertNull(page.getFetchedAt(), "a filtered list is not the cached page");
		TransactionPageRequestDto next = new TransactionPageRequestDto();
		next.setSize(2);
		next.setCursor(page.getNextCursor());
		service.list(next);
		verify(dao).findPage(argThat(p -> "2026-11-25".equals(p.getCursorDay()) && "2026-11-25T10:00:00.123456Z".equals(p.getCursorCreated())
				&& "a1111111-2222-3333-4444-555555555555".equals(p.getCursorId())));
		verify(dao).findPage(argThat(p -> "expense".equals(p.getKind()) && "dinner".equals(p.getSearchText())));
	}

	@Test
	void aBadRequestIsRefusedBeforeTheTableIsRead() {
		for (var change : List.<java.util.function.Consumer<TransactionPageRequestDto>>of(r -> r.setSize(0), r -> r.setSize(201), r -> r.setKind("gift"), r -> r.setAccountId("x"),
				r -> r.setFrom("2026-13-40"), r -> r.setTo("yesterday"), r -> {
					r.setFrom("2026-11-30");
					r.setTo("2026-11-01");
				}, r -> r.setQ("x".repeat(101)), r -> r.setCursor("not a cursor"), r -> r.setCursor("W10"))) {
			TransactionPageRequestDto request = new TransactionPageRequestDto();
			change.accept(request);
			assertEquals(BaseError.INVALID_REQUEST, assertThrows(KarthiException.class, () -> service.list(request)).getMessage());
		}
		verify(dao, never()).findPage(any());
	}

	private TransactionCreateRequestDto newEntry() {
		TransactionCreateRequestDto request = new TransactionCreateRequestDto();
		request.setKind("expense");
		request.setCategory(" Food ");
		request.setDescription("Dinner");
		request.setAmount(new BigDecimal("450.456"));
		request.setDate("2026-11-26");
		return request;
	}

	private WealthAccountEntity account() {
		WealthAccountEntity account = new WealthAccountEntity();
		account.setId(ACCOUNT);
		account.setAccountName("HDFC Salary");
		account.setMaskedIdentifier("4521");
		return account;
	}

	@Test
	void anEntryIsStoredForTheCallerOnTheirOwnAccountAndTheFirstPageIsDropped() {
		when(accounts.findById(any())).thenReturn(account());
		when(dao.insert(any())).thenAnswer(invocation -> invocation.getArgument(0));
		TransactionCreateRequestDto request = newEntry();
		request.setAccountId(ACCOUNT);
		request.setExternalRef(" ref-1 ");

		var saved = service.create(request);

		verify(dao).insert(argThat(e -> USER.equals(e.getUserId()) && "Food".equals(e.getCategory()) && new BigDecimal("450.46").equals(e.getAmount()) && ACCOUNT.equals(e.getAccountId())
				&& "ref-1".equals(e.getExternalRef()) && "2026-11-26".equals(e.getOccurredOn())));
		verify(nexus).evict("transactions", USER);
		assertEquals("HDFC Salary 4521", saved.getAccount());
	}

	@Test
	void anAccountNameThatMatchesIsUsedAndOneThatDoesNotLeavesTheEntryUnassigned() {
		when(dao.insert(any())).thenAnswer(invocation -> invocation.getArgument(0));
		TransactionCreateRequestDto byName = newEntry();
		byName.setAccount("hdfc salary 4521");
		when(dao.findAccountIdByName(USER, "hdfc salary 4521")).thenReturn(ACCOUNT);
		when(accounts.findById(any())).thenReturn(account());
		assertEquals(ACCOUNT, service.create(byName).getAccountId());

		TransactionCreateRequestDto unknown = newEntry();
		unknown.setAccount("nobody");
		var saved = service.create(unknown);
		assertNull(saved.getAccountId());
		assertEquals("Unassigned", saved.getAccount());
	}

	@Test
	void theKindCategoryAmountDateAndAccountAreChecked() {
		for (var change : List.<java.util.function.Consumer<TransactionCreateRequestDto>>of(r -> r.setKind("gift"), r -> r.setCategory(" "), r -> r.setAmount(BigDecimal.ZERO),
				r -> r.setAmount(new BigDecimal("2000000000000")), r -> r.setAmount(null), r -> r.setDate("2026-02-30"), r -> r.setDate(""))) {
			TransactionCreateRequestDto request = newEntry();
			change.accept(request);
			assertEquals(BaseError.TRANSACTION_INVALID, assertThrows(KarthiException.class, () -> service.create(request)).getMessage());
		}
		TransactionCreateRequestDto notMine = newEntry();
		notMine.setAccountId(ACCOUNT);
		when(accounts.findById(any())).thenReturn(null);
		assertEquals(BaseError.TRANSACTION_ACCOUNT_INVALID, assertThrows(KarthiException.class, () -> service.create(notMine)).getMessage());
		TransactionCreateRequestDto malformed = newEntry();
		malformed.setAccountId("x");
		assertEquals(BaseError.TRANSACTION_ACCOUNT_INVALID, assertThrows(KarthiException.class, () -> service.create(malformed)).getMessage());
		assertEquals(BaseError.INVALID_REQUEST, assertThrows(KarthiException.class, () -> service.create(null)).getMessage());
		verify(dao, never()).insert(any());
	}

	@Test
	void aReferenceThatWasRecordedBeforeIsRefused() {
		org.mockito.Mockito.doReturn(null).when(dao).insert(any());
		TransactionCreateRequestDto request = newEntry();
		request.setExternalRef("ref-1");

		assertEquals(BaseError.TRANSACTION_REFERENCE_EXISTS, assertThrows(KarthiException.class, () -> service.create(request)).getMessage());
		verify(nexus, never()).evict(any(), any());
	}

	@Test
	void deletingRemovesTheCallersEntryDropsTheCacheAndAnUnknownIdIsFalse() {
		IdRequestDto request = new IdRequestDto();
		request.setId(ACCOUNT);
		when(dao.delete(USER, ACCOUNT)).thenReturn(true);

		assertTrue(service.delete(request));
		verify(nexus).evict("transactions", USER);

		when(dao.delete(USER, ACCOUNT)).thenReturn(false);
		assertEquals(false, service.delete(request));
		IdRequestDto bad = new IdRequestDto();
		bad.setId("x");
		assertEquals(BaseError.INVALID_REQUEST, assertThrows(KarthiException.class, () -> service.delete(bad)).getMessage());
	}

	@Test
	void refreshReadsTheFirstPageAgain() {
		when(dao.findPage(any())).thenReturn(List.of());
		assertTrue(service.refresh().getItems().isEmpty());
		verify(nexus).hardRefresh(any(), any(Class.class), any(java.util.function.Supplier.class));
	}
}
