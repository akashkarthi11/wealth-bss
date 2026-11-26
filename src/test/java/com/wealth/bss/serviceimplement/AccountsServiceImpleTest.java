package com.wealth.bss.serviceimplement;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wealth.bss.common.model.dao.IfWealthAccountBalanceDao;
import com.wealth.bss.common.model.dao.IfWealthAccountDao;
import com.wealth.bss.common.model.dao.IfWealthAccountSplitDao;
import com.wealth.bss.common.model.dto.PaginationDto;
import com.wealth.bss.common.model.entity.WealthAccountBalanceEntity;
import com.wealth.bss.common.model.entity.WealthAccountEntity;
import com.wealth.bss.common.model.entity.WealthAccountSplitBalanceEntity;
import com.wealth.bss.common.model.entity.WealthAccountSplitEntity;
import com.wealth.bss.common.model.entity.WealthAccountSplitMoveEntity;
import com.wealth.bss.common.model.entity.WealthAccountSubtypeEntity;
import com.wealth.common.generic.BaseError;
import com.wealth.common.generic.KarthiException;
import com.wealth.common.model.dto.AccountIdRequestDto;
import com.wealth.common.model.dto.IdRequestDto;
import com.wealth.common.model.dto.account.AccountDetailDto;
import com.wealth.common.model.dto.account.AccountSplitDto;
import com.wealth.common.model.dto.account.AccountsOverviewDto;
import com.wealth.common.utils.generic.RequestHeaderUtil;

class AccountsServiceImpleTest {

	private static final String USER_ID = "2a7014a8-9cf0-4d39-bed2-c83a83eddf0c";

	private IfWealthAccountDao accounts;
	private IfWealthAccountBalanceDao balances;
	private IfWealthAccountSplitDao splits;
	private AccountsServiceImple service;

	@BeforeEach
	void setUp() {
		accounts = mock(IfWealthAccountDao.class);
		balances = mock(IfWealthAccountBalanceDao.class);
		splits = mock(IfWealthAccountSplitDao.class);
		service = new AccountsServiceImple(accounts, balances, splits);
		RequestHeaderUtil.setUserId(USER_ID);
		when(accounts.findAllSubtypes()).thenReturn(List.of(subtype("savings", "CASA"), subtype("fd", "INVESTMENT"), subtype("card", "LIABILITY")));
	}

	@AfterEach
	void tearDown() {
		RequestHeaderUtil.clear();
	}

	private WealthAccountSubtypeEntity subtype(String code, String category) {
		WealthAccountSubtypeEntity e = new WealthAccountSubtypeEntity();
		e.setCode(code);
		e.setCategory(category);
		return e;
	}

	private WealthAccountEntity account(String id, String bank, String subtype, String currency, String status) {
		WealthAccountEntity e = new WealthAccountEntity();
		e.setId(id);
		e.setBankName(bank);
		e.setAccountName(bank + " " + subtype);
		e.setSubtypeCode(subtype);
		e.setCurrency(currency);
		e.setStatus(status);
		return e;
	}

	private WealthAccountBalanceEntity balance(String accountId, String amount, String status) {
		WealthAccountBalanceEntity e = new WealthAccountBalanceEntity();
		e.setAccountId(accountId);
		e.setBalance(new BigDecimal(amount));
		e.setStatus(status);
		e.setAsOf(Timestamp.valueOf("2026-11-20 10:00:00"));
		return e;
	}

	@Test
	void overviewGroupsByBankAndAddsTotalsByCategory() {
		when(accounts.findByUser(any())).thenReturn(List.of(account("a1", "Atom", "savings", "INR", "active"), account("a2", "Atom", "fd", "INR", "active"),
				account("a3", "HDFC", "card", "INR", "active"), account("a4", "Old", "savings", "INR", "closed")));
		when(balances.findLatestByUser(any())).thenReturn(List.of(balance("a1", "1000.50", "verified"), balance("a2", "5000", "reported"),
				balance("a3", "300", "verified"), balance("a4", "99", "verified")));

		AccountsOverviewDto overview = service.overview();

		assertEquals(2, overview.getBanks().size());
		assertEquals("Atom", overview.getBanks().get(0).getName());
		assertEquals(2, overview.getBanks().get(0).getAccounts().size());
		assertEquals(0, new BigDecimal("1000.50").compareTo(overview.getTotals().getCasa()));
		assertEquals(0, new BigDecimal("5000").compareTo(overview.getTotals().getInvestment()));
		assertEquals(0, new BigDecimal("300").compareTo(overview.getTotals().getLiability()));
		assertEquals(0, new BigDecimal("5700.50").compareTo(overview.getTotals().getNetWorth()));
		assertEquals("CASA", overview.getBanks().get(0).getAccounts().get(0).getCategory());
		assertEquals(0, new BigDecimal("6000.50").compareTo(overview.getBanks().get(0).getTotals().getNetWorth()));
	}

	@Test
	void overviewLeavesOutForeignAndPendingBalancesFromTotalsButKeepsTheCards() {
		when(accounts.findByUser(any())).thenReturn(List.of(account("a1", "Wise", "savings", "USD", "active"), account("a2", "Atom", "savings", "INR", "active")));
		when(balances.findLatestByUser(any())).thenReturn(List.of(balance("a1", "50", "verified"), balance("a2", "70", "pending_reconciliation")));

		AccountsOverviewDto overview = service.overview();

		assertEquals(0, BigDecimal.ZERO.compareTo(overview.getTotals().getCasa()));
		assertEquals(0, new BigDecimal("50").compareTo(overview.getBanks().get(0).getAccounts().get(0).getBalance()));
		assertEquals(2, overview.getBanks().size());
	}

	@Test
	void overviewIsEmptyForAUserWithNoAccounts() {
		when(accounts.findByUser(any())).thenReturn(List.of());
		when(balances.findLatestByUser(any())).thenReturn(List.of());

		AccountsOverviewDto overview = service.overview();

		assertTrue(overview.getBanks().isEmpty());
		assertEquals(0, BigDecimal.ZERO.compareTo(overview.getTotals().getNetWorth()));
	}

	@Test
	void overviewReadsOnlyTheCallersRows() {
		when(accounts.findByUser(any())).thenReturn(List.of());
		when(balances.findLatestByUser(any())).thenReturn(List.of());

		service.overview();

		verify(accounts).findByUser(argThat((PaginationDto p) -> USER_ID.equals(p.getUserId())));
		verify(balances).findLatestByUser(argThat((PaginationDto p) -> USER_ID.equals(p.getUserId())));
	}

	@Test
	void detailOfAnotherUsersAccountIsRefusedAndNothingElseIsRead() {
		when(accounts.findById(any())).thenReturn(null);
		IdRequestDto request = new IdRequestDto();
		request.setId("11111111-1111-1111-1111-111111111111");

		assertEquals(BaseError.WEALTH_ACCOUNT_NOT_FOUND, assertThrows(KarthiException.class, () -> service.detail(request)).getMessage());
		verify(accounts).findById(argThat((PaginationDto p) -> USER_ID.equals(p.getUserId()) && "11111111-1111-1111-1111-111111111111".equals(p.getId())));
		verify(balances, never()).findHistory(any());
		verify(splits, never()).findByAccount(any());
	}

	@Test
	void detailNeedsAnId() {
		assertEquals(BaseError.INVALID_REQUEST, assertThrows(KarthiException.class, () -> service.detail(new IdRequestDto())).getMessage());
		assertEquals(BaseError.INVALID_REQUEST, assertThrows(KarthiException.class, () -> service.detail(null)).getMessage());
	}

	@Test
	void aMalformedIdIsRefusedBeforeTheDatabaseIsAsked() {
		IdRequestDto request = new IdRequestDto();
		request.setId("1; drop table");
		AccountIdRequestDto splitRequest = new AccountIdRequestDto();
		splitRequest.setAccountId("abc");

		assertEquals(BaseError.INVALID_REQUEST, assertThrows(KarthiException.class, () -> service.detail(request)).getMessage());
		assertEquals(BaseError.INVALID_REQUEST, assertThrows(KarthiException.class, () -> service.splits(splitRequest)).getMessage());
		verify(accounts, never()).findById(any());
	}

	@Test
	void detailReturnsTheAccountItsBalanceHistoryAndSplits() {
		when(accounts.findById(any())).thenReturn(account("a1", "Atom", "savings", "INR", "active"));
		when(balances.findHistory(any())).thenReturn(List.of(balance("a1", "900", "verified"), balance("a1", "800", "reported")));
		when(splits.findByAccount(any())).thenReturn(List.of());
		IdRequestDto request = new IdRequestDto();
		request.setId("22222222-2222-2222-2222-222222222222");

		AccountDetailDto detail = service.detail(request);

		assertEquals("Atom", detail.getAccount().getBankName());
		assertEquals(0, new BigDecimal("900").compareTo(detail.getAccount().getBalance()));
		assertEquals("verified", detail.getAccount().getBalanceStatus());
		assertEquals(2, detail.getBalances().size());
		assertTrue(detail.getSplits().isEmpty());
	}

	@Test
	void splitsAddTheMovesMadeAfterTheSavedBalance() {
		WealthAccountSplitEntity rent = new WealthAccountSplitEntity();
		rent.setId("s1");
		rent.setName("Rent");
		WealthAccountSplitBalanceEntity saved = new WealthAccountSplitBalanceEntity();
		saved.setSplitId("s1");
		saved.setBalance(new BigDecimal("1000"));
		saved.setStatus("reported");
		saved.setAsOf(Timestamp.valueOf("2026-11-10 09:00:00"));
		WealthAccountSplitMoveEntity before = move("s1", "-50", "2026-11-09 09:00:00");
		WealthAccountSplitMoveEntity after = move("s1", "-200", "2026-11-12 09:00:00");
		when(accounts.findById(any())).thenReturn(account("a1", "Atom", "savings", "INR", "active"));
		when(splits.findByAccount(any())).thenReturn(List.of(rent));
		when(splits.findLatestBalancesByAccount(any())).thenReturn(List.of(saved));
		when(splits.findMovesByAccount(any())).thenReturn(List.of(after, before));
		AccountIdRequestDto request = new AccountIdRequestDto();
		request.setAccountId("22222222-2222-2222-2222-222222222222");

		List<AccountSplitDto> result = service.splits(request);

		assertEquals(1, result.size());
		assertEquals(0, new BigDecimal("800").compareTo(result.get(0).getEstimatedBalance()));
		assertEquals("estimated", result.get(0).getBalanceStatus());
		assertEquals(2, result.get(0).getMoves().size());
	}

	@Test
	void aSplitWithNoBalanceAndNoMovesHasNoEstimate() {
		WealthAccountSplitEntity goal = new WealthAccountSplitEntity();
		goal.setId("s2");
		when(accounts.findById(any())).thenReturn(account("a1", "Atom", "savings", "INR", "active"));
		when(splits.findByAccount(any())).thenReturn(List.of(goal));
		when(splits.findLatestBalancesByAccount(any())).thenReturn(List.of());
		when(splits.findMovesByAccount(any())).thenReturn(List.of());
		AccountIdRequestDto request = new AccountIdRequestDto();
		request.setAccountId("22222222-2222-2222-2222-222222222222");

		AccountSplitDto only = service.splits(request).get(0);

		assertNull(only.getEstimatedBalance());
		assertNull(only.getBalanceStatus());
	}

	@Test
	void splitsOfAnotherUsersAccountAreRefused() {
		when(accounts.findById(any())).thenReturn(null);
		AccountIdRequestDto request = new AccountIdRequestDto();
		request.setAccountId("11111111-1111-1111-1111-111111111111");

		assertEquals(BaseError.WEALTH_ACCOUNT_NOT_FOUND, assertThrows(KarthiException.class, () -> service.splits(request)).getMessage());
		verify(splits, never()).findByAccount(any());
	}

	private WealthAccountSplitMoveEntity move(String splitId, String amount, String at) {
		WealthAccountSplitMoveEntity m = new WealthAccountSplitMoveEntity();
		m.setSplitId(splitId);
		m.setAmount(new BigDecimal(amount));
		m.setOccurredAt(Timestamp.valueOf(at));
		return m;
	}
}
