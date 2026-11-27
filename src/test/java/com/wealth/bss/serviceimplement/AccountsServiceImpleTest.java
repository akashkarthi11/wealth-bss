package com.wealth.bss.serviceimplement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
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
import com.wealth.bss.common.model.dao.IfWealthInvestmentDao;
import com.wealth.bss.common.model.dto.PaginationDto;
import com.wealth.bss.common.model.entity.WealthAccountBalanceEntity;
import com.wealth.bss.nexus.service.IfNexusReadService;
import com.wealth.bss.serviceinterface.IfRequestContextService;
import com.wealth.common.model.dto.CachedValueDto;
import com.wealth.bss.common.model.entity.WealthAccountEntity;
import com.wealth.bss.common.model.entity.WealthAccountSplitBalanceEntity;
import com.wealth.bss.common.model.entity.WealthAccountSplitEntity;
import com.wealth.bss.common.model.entity.WealthAccountSplitMoveEntity;
import com.wealth.bss.common.model.entity.WealthAccountSubtypeEntity;
import com.wealth.common.generic.BaseError;
import com.wealth.common.generic.KarthiException;
import com.wealth.common.model.dto.AccountIdRequestDto;
import com.wealth.common.model.dto.IdRequestDto;
import com.wealth.common.model.dto.account.AccountCreateRequestDto;
import com.wealth.common.model.dto.account.AccountDetailDto;
import com.wealth.common.model.dto.account.FdTermsDto;
import com.wealth.common.model.dto.account.AccountSplitDto;
import com.wealth.common.model.dto.account.AccountsOverviewDto;
import com.wealth.common.utils.generic.RequestHeaderUtil;

class AccountsServiceImpleTest {

	private static final String USER_ID = "2a7014a8-9cf0-4d39-bed2-c83a83eddf0c";

	private IfWealthAccountDao accounts;
	private IfWealthAccountBalanceDao balances;
	private IfWealthAccountSplitDao splits;
	private IfWealthInvestmentDao investments;
	private IfNexusReadService nexus;
	private IfRequestContextService context;
	private AccountsServiceImple service;

	@BeforeEach
	void setUp() {
		accounts = mock(IfWealthAccountDao.class);
		balances = mock(IfWealthAccountBalanceDao.class);
		splits = mock(IfWealthAccountSplitDao.class);
		investments = mock(IfWealthInvestmentDao.class);
		nexus = mock(IfNexusReadService.class);
		nexusPassesThrough(nexus);
		context = mock(IfRequestContextService.class);
		service = new AccountsServiceImple(accounts, balances, splits, investments, nexus, context);
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

	// The nexus is not under test here: it hands the loader's result straight back, stamped with a read time.
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private void nexusPassesThrough(IfNexusReadService nexus) {
		org.mockito.stubbing.Answer<Object> answer = invocation -> {
			CachedValueDto cached = new CachedValueDto();
			cached.setData(((java.util.function.Supplier) invocation.getArgument(2)).get());
			cached.setFetchedAt(java.sql.Timestamp.valueOf("2026-11-27 10:00:00"));
			return cached;
		};
		when(nexus.read(any(), any(Class.class), any(java.util.function.Supplier.class))).thenAnswer(answer);
		when(nexus.hardRefresh(any(), any(Class.class), any(java.util.function.Supplier.class))).thenAnswer(answer);
	}

	@Test
	void theOverviewComesThroughTheNexusStampedWithWhenItWasReadAndWhetherItIsStale() {
		when(accounts.findByUser(any())).thenReturn(List.of());
		when(balances.findLatestByUser(any())).thenReturn(List.of());

		AccountsOverviewDto overview = service.overview();

		verify(nexus).read(org.mockito.ArgumentMatchers.eq("accounts-overview"), org.mockito.ArgumentMatchers.eq(AccountsOverviewDto.class), any());
		assertEquals(Timestamp.valueOf("2026-11-27 10:00:00"), overview.getFetchedAt());
		assertEquals(false, overview.isStale());
	}

	@Test
	void theRefreshButtonAsksTheNexusForAHardRefresh() {
		when(accounts.findByUser(any())).thenReturn(List.of());
		when(balances.findLatestByUser(any())).thenReturn(List.of());

		service.refresh();

		verify(nexus).hardRefresh(org.mockito.ArgumentMatchers.eq("accounts-overview"), org.mockito.ArgumentMatchers.eq(AccountsOverviewDto.class), any());
	}

	private WealthAccountSubtypeEntity dematSubtype() {
		WealthAccountSubtypeEntity e = subtype("demat_equity", "INVESTMENT");
		e.setValuation("holdings");
		return e;
	}

	@Test
	void aHoldingsAccountCountsAtTheMarketValueOfItsHoldingsButIsNotListedAsABank() {
		when(accounts.findAllSubtypes()).thenReturn(List.of(subtype("savings", "CASA"), dematSubtype()));
		when(accounts.findByUser(any())).thenReturn(List.of(account("a1", "Atom", "savings", "INR", "active"), account("d1", "Angel One", "demat_equity", "INR", "active")));
		when(balances.findLatestByUser(any())).thenReturn(List.of(balance("a1", "1000", "verified")));
		when(investments.findHoldingValuesByUser(any())).thenReturn(List.of(balance("d1", "239339.88", "ignored")));

		AccountsOverviewDto overview = service.overview();

		assertEquals(1, overview.getBanks().size(), "the demat is an investment, not a bank");
		assertEquals(1, overview.getInvestmentAccounts().size());
		assertEquals("Angel One", overview.getInvestmentAccounts().get(0).getBankName());
		assertEquals("Atom", overview.getBanks().get(0).getName());
		assertEquals(0, new BigDecimal("1000").compareTo(overview.getBanks().get(0).getTotals().getNetWorth()));
		assertEquals(0, new BigDecimal("239339.88").compareTo(overview.getTotals().getInvestment()));
		assertEquals(0, new BigDecimal("240339.88").compareTo(overview.getTotals().getNetWorth()));
	}

	@Test
	void aHoldingsAccountIsNotValuedByItsOwnBalanceRowsWhichHoldTheBrokerCashAndOtherAccountsIgnoreHoldingValues() {
		when(accounts.findAllSubtypes()).thenReturn(List.of(subtype("savings", "CASA"), dematSubtype()));
		when(accounts.findByUser(any())).thenReturn(List.of(account("a1", "Atom", "savings", "INR", "active"), account("d1", "Angel One", "demat_equity", "INR", "active")));
		when(balances.findLatestByUser(any())).thenReturn(List.of(balance("a1", "1000", "verified"), balance("d1", "500", "reported")));
		when(investments.findHoldingValuesByUser(any())).thenReturn(List.of(balance("a1", "999999", "x")));

		AccountsOverviewDto overview = service.overview();

		assertEquals(0, BigDecimal.ZERO.compareTo(overview.getTotals().getInvestment()), "the 500 is Angel One cash, not the value of the account");
		assertEquals(0, new BigDecimal("1000").compareTo(overview.getTotals().getCasa()));
	}

	@Test
	void theAccountDetailOfAHoldingsAccountShowsTheHoldingsValue() {
		String id = "2a7014a8-9cf0-4d39-bed2-c83a83eddf0d";
		when(accounts.findAllSubtypes()).thenReturn(List.of(dematSubtype()));
		when(accounts.findById(any())).thenReturn(account(id, "Angel One", "demat_equity", "INR", "active"));
		when(balances.findHistory(any())).thenReturn(List.of());
		when(investments.findHoldingValuesByUser(any())).thenReturn(List.of(balance(id, "1234.50", "x")));
		IdRequestDto request = new IdRequestDto();
		request.setId(id);

		AccountDetailDto detail = service.detail(request);

		assertEquals(0, new BigDecimal("1234.50").compareTo(detail.getAccount().getBalance()));
		assertEquals("estimated", detail.getAccount().getBalanceStatus());
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

	// ---- adding an account (admin) ----

	private WealthAccountSubtypeEntity typed(String code, String category) {
		return subtype(code, category);
	}

	private AccountCreateRequestDto newAccount(String subtype, String institution, String name) {
		AccountCreateRequestDto request = new AccountCreateRequestDto();
		request.setSubtype(subtype);
		request.setInstitution(institution);
		request.setName(name);
		return request;
	}

	private void knownTypes() {
		when(accounts.findAllSubtypes()).thenReturn(List.of(typed("savings", "CASA"), typed("fixed_deposit", "INVESTMENT"), typed("us_brokerage", "INVESTMENT"), typed("digital_gold", "INVESTMENT")));
		when(accounts.insert(any())).thenAnswer(invocation -> {
			WealthAccountEntity saved = invocation.getArgument(0);
			saved.setId("new-id");
			return saved;
		});
	}

	@Test
	void anAdminAddsAnAccountForThemselvesAndTheCachedListsAreDropped() {
		knownTypes();
		AccountCreateRequestDto request = newAccount("savings", "  HDFC Bank ", "Main account");
		request.setMaskedIdentifier("1234");

		var created = service.create(request);

		verify(accounts).insert(argThat(e -> USER_ID.equals(e.getUserId()) && "hdfc-bank-savings-main-account-1234".equals(e.getAccountKey()) && "HDFC Bank".equals(e.getBankName())
				&& "Main account".equals(e.getAccountName()) && "INR".equals(e.getCurrency()) && "1234".equals(e.getMaskedIdentifier())));
		verify(nexus).evict("accounts-overview", USER_ID);
		verify(nexus).evict("investments", USER_ID);
		assertEquals("CASA", created.getCategory());
		assertEquals("new-id", created.getId());
	}

	@Test
	void anInvestmentThatIsNotADematIsAddedTheSameWayAndAUsBrokerageDefaultsToDollars() {
		knownTypes();

		service.create(newAccount("digital_gold", "Augmont", "Gold locker"));
		service.create(newAccount("us_brokerage", "INDmoney", "US stocks"));

		verify(accounts).insert(argThat(e -> "digital_gold".equals(e.getSubtypeCode()) && "INR".equals(e.getCurrency())));
		verify(accounts).insert(argThat(e -> "us_brokerage".equals(e.getSubtypeCode()) && "USD".equals(e.getCurrency())));
	}

	@Test
	void aNonAdminAddsNothing() {
		knownTypes();
		doThrow(new KarthiException(BaseError.ADMIN_ONLY_ACTION)).when(context).requireAdmin();

		assertEquals(BaseError.ADMIN_ONLY_ACTION, assertThrows(KarthiException.class, () -> service.create(newAccount("savings", "HDFC", "Main"))).getMessage());
		verify(accounts, never()).insert(any());
	}

	@Test
	void theTypeNamesAndCurrencyAreChecked() {
		knownTypes();

		assertEquals(BaseError.ACCOUNT_TYPE_INVALID, assertThrows(KarthiException.class, () -> service.create(newAccount("nope", "HDFC", "Main"))).getMessage());
		assertEquals(BaseError.ACCOUNT_DETAILS_INVALID, assertThrows(KarthiException.class, () -> service.create(newAccount("savings", " ", "Main"))).getMessage());
		assertEquals(BaseError.ACCOUNT_DETAILS_INVALID, assertThrows(KarthiException.class, () -> service.create(newAccount("savings", "HDFC", "x".repeat(81)))).getMessage());
		AccountCreateRequestDto euro = newAccount("savings", "HDFC", "Main");
		euro.setCurrency("EUR");
		assertEquals(BaseError.ACCOUNT_CURRENCY_INVALID, assertThrows(KarthiException.class, () -> service.create(euro)).getMessage());
		assertEquals(BaseError.INVALID_REQUEST, assertThrows(KarthiException.class, () -> service.create(null)).getMessage());
		verify(accounts, never()).insert(any());
	}

	@Test
	void fixedDepositTermsAreAcceptedOnlyForAFixedDepositAndMustMakeSense() {
		knownTypes();
		FdTermsDto terms = new FdTermsDto();
		terms.setPrincipal(new BigDecimal("100000"));
		terms.setBookedOn(java.sql.Date.valueOf("2026-01-01"));
		terms.setMaturityOn(java.sql.Date.valueOf("2027-01-01"));
		terms.setPayout("cumulative");
		terms.setCompounding("quarterly");
		AccountCreateRequestDto fd = newAccount("fixed_deposit", "SBI", "FD 1");
		fd.setFdTerms(terms);

		service.create(fd);
		verify(accounts).insert(argThat(e -> new BigDecimal("100000").equals(e.getFdPrincipal()) && "quarterly".equals(e.getFdCompounding())));

		AccountCreateRequestDto onSavings = newAccount("savings", "SBI", "Main");
		onSavings.setFdTerms(terms);
		assertEquals(BaseError.FD_TERMS_INVALID, assertThrows(KarthiException.class, () -> service.create(onSavings)).getMessage());
		terms.setMaturityOn(java.sql.Date.valueOf("2025-01-01"));
		assertEquals(BaseError.FD_TERMS_INVALID, assertThrows(KarthiException.class, () -> service.create(fd)).getMessage(), "maturity must be after booking");
		terms.setMaturityOn(java.sql.Date.valueOf("2027-01-01"));
		terms.setPayout("daily");
		assertEquals(BaseError.FD_TERMS_INVALID, assertThrows(KarthiException.class, () -> service.create(fd)).getMessage());
	}

	@Test
	void theSameInstitutionTypeAndNameIsRefusedTheSecondTime() {
		knownTypes();
		org.mockito.Mockito.doReturn(null).when(accounts).insert(any());

		assertEquals(BaseError.ACCOUNT_EXISTS, assertThrows(KarthiException.class, () -> service.create(newAccount("savings", "HDFC", "Main"))).getMessage());
		verify(nexus, never()).evict(any(), any());
	}

	// ---- recording a balance and changing FD terms (admin) ----

	private static final String ACCOUNT_ID = "11111111-2222-3333-4444-555555555555";

	private com.wealth.common.model.dto.account.BalanceRecordRequestDto balanceOf(String balance, String status) {
		com.wealth.common.model.dto.account.BalanceRecordRequestDto request = new com.wealth.common.model.dto.account.BalanceRecordRequestDto();
		request.setAccountId(ACCOUNT_ID);
		request.setBalance(new BigDecimal(balance));
		request.setStatus(status);
		return request;
	}

	private void depositAccount(String subtype, String status) {
		WealthAccountSubtypeEntity fd = typed("fixed_deposit", "INVESTMENT");
		fd.setValuation("balance");
		WealthAccountSubtypeEntity savings = typed("savings", "CASA");
		savings.setValuation("balance");
		when(accounts.findAllSubtypes()).thenReturn(List.of(fd, savings, dematSubtype()));
		when(accounts.findById(any())).thenReturn(account(ACCOUNT_ID, "SBI", subtype, "INR", status));
		when(balances.insert(any())).thenAnswer(invocation -> invocation.getArgument(0));
	}

	@Test
	void anAdminRecordsAVerifiedBalanceOnAFixedDepositAndTheListsAreDropped() {
		depositAccount("fixed_deposit", "active");

		var result = service.recordBalance(balanceOf("105000.50", null));

		assertTrue(result.isStored());
		verify(balances).insert(argThat(e -> "verified".equals(e.getStatus()) && "owner".equals(e.getEnteredBy()) && USER_ID.equals(e.getUserId()) && ACCOUNT_ID.equals(e.getAccountId())));
		verify(balances, never()).updateAccountCopy(any());
		verify(nexus).evict("accounts-overview", USER_ID);
	}

	@Test
	void aVerifiedSavingsBalanceIsAlsoCopiedOntoTheAccountButAReportedOneIsNot() {
		depositAccount("savings", "active");

		service.recordBalance(balanceOf("500", "verified"));
		service.recordBalance(balanceOf("600", "reported"));

		verify(balances).updateAccountCopy(argThat(e -> new BigDecimal("500").equals(e.getBalance())));
		verify(balances, never()).updateAccountCopy(argThat(e -> new BigDecimal("600").equals(e.getBalance())));
	}

	@Test
	void aBalanceIsRefusedForAHoldingsAccountAnInactiveAccountAnotherUsersAccountAndBadValues() {
		depositAccount("demat_equity", "active");
		assertEquals(BaseError.BALANCE_NOT_ALLOWED, assertThrows(KarthiException.class, () -> service.recordBalance(balanceOf("1", null))).getMessage());
		depositAccount("fixed_deposit", "closed");
		assertEquals(BaseError.BALANCE_NOT_ALLOWED, assertThrows(KarthiException.class, () -> service.recordBalance(balanceOf("1", null))).getMessage());
		depositAccount("fixed_deposit", "active");
		assertEquals(BaseError.BALANCE_INVALID, assertThrows(KarthiException.class, () -> service.recordBalance(balanceOf("1", "pending_reconciliation"))).getMessage());
		assertEquals(BaseError.BALANCE_INVALID, assertThrows(KarthiException.class, () -> service.recordBalance(balanceOf("2000000000000", null))).getMessage());
		var future = balanceOf("1", null);
		future.setAsOf(new Timestamp(System.currentTimeMillis() + 3_600_000L));
		assertEquals(BaseError.BALANCE_INVALID, assertThrows(KarthiException.class, () -> service.recordBalance(future)).getMessage());
		var bad = balanceOf("1", null);
		bad.setAccountId("nope");
		assertEquals(BaseError.INVALID_REQUEST, assertThrows(KarthiException.class, () -> service.recordBalance(bad)).getMessage());
		when(accounts.findById(any())).thenReturn(null);
		assertEquals(BaseError.WEALTH_ACCOUNT_NOT_FOUND, assertThrows(KarthiException.class, () -> service.recordBalance(balanceOf("1", null))).getMessage());
		verify(balances, never()).insert(any());
	}

	@Test
	void aDryRunStoresNothingAndASecondBalanceAtTheSameMomentIsReportedNotStored() {
		depositAccount("fixed_deposit", "active");
		var dry = balanceOf("10", null);
		dry.setDryRun(true);

		var preview = service.recordBalance(dry);

		assertTrue(!preview.isStored());
		verify(balances, never()).insert(any());
		org.mockito.Mockito.doReturn(null).when(balances).insert(any());
		var again = service.recordBalance(balanceOf("10", null));
		assertTrue(!again.isStored());
		assertEquals("already_stored", again.getReason());
	}

	@Test
	void aNonAdminRecordsNoBalanceAndChangesNoTerms() {
		depositAccount("fixed_deposit", "active");
		doThrow(new KarthiException(BaseError.ADMIN_ONLY_ACTION)).when(context).requireAdmin();
		com.wealth.common.model.dto.account.FdTermsRequestDto terms = new com.wealth.common.model.dto.account.FdTermsRequestDto();
		terms.setAccountId(ACCOUNT_ID);
		terms.setFdTerms(new FdTermsDto());

		assertEquals(BaseError.ADMIN_ONLY_ACTION, assertThrows(KarthiException.class, () -> service.recordBalance(balanceOf("1", null))).getMessage());
		assertEquals(BaseError.ADMIN_ONLY_ACTION, assertThrows(KarthiException.class, () -> service.updateFdTerms(terms)).getMessage());
		verify(balances, never()).insert(any());
		verify(accounts, never()).updateFdTerms(any());
	}

	@Test
	void fdTermsChangeOnlyForAFixedDepositAndKeepTheTermsThatAreNotSent() {
		depositAccount("fixed_deposit", "active");
		when(accounts.updateFdTerms(any())).thenAnswer(invocation -> {
			WealthAccountEntity saved = invocation.getArgument(0);
			saved.setSubtypeCode("fixed_deposit");
			return saved;
		});
		FdTermsDto terms = new FdTermsDto();
		terms.setMaturityOn(java.sql.Date.valueOf("2027-06-01"));
		com.wealth.common.model.dto.account.FdTermsRequestDto request = new com.wealth.common.model.dto.account.FdTermsRequestDto();
		request.setAccountId(ACCOUNT_ID);
		request.setFdTerms(terms);

		service.updateFdTerms(request);

		verify(accounts).updateFdTerms(argThat(e -> USER_ID.equals(e.getUserId()) && ACCOUNT_ID.equals(e.getId()) && e.getFdPrincipal() == null && e.getFdMaturityOn() != null));
		verify(nexus).evict("accounts-overview", USER_ID);

		depositAccount("savings", "active");
		assertEquals(BaseError.FD_TERMS_INVALID, assertThrows(KarthiException.class, () -> service.updateFdTerms(request)).getMessage());
		depositAccount("fixed_deposit", "active");
		terms.setPrincipal(BigDecimal.ZERO);
		assertEquals(BaseError.FD_TERMS_INVALID, assertThrows(KarthiException.class, () -> service.updateFdTerms(request)).getMessage());
	}
}
