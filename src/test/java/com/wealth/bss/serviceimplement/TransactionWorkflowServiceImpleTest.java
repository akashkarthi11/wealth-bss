package com.wealth.bss.serviceimplement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wealth.bss.common.model.dao.IfWealthAccountDao;
import com.wealth.bss.common.model.dao.IfWealthTransactionDao;
import com.wealth.bss.common.model.dao.IfWealthTransactionWorkflowDao;
import com.wealth.bss.common.model.entity.WealthTransactionEntity;
import com.wealth.bss.common.model.entity.WealthTransactionWorkflowEntity;
import com.wealth.bss.nexus.service.IfNexusReadService;
import com.wealth.common.generic.BaseError;
import com.wealth.common.generic.KarthiException;
import com.wealth.common.model.dto.transaction.BankImportResultDto;
import com.wealth.common.model.dto.transaction.BankLineDto;
import com.wealth.common.model.dto.transaction.WorkflowDecisionRequestDto;
import com.wealth.common.utils.generic.RequestHeaderUtil;

class TransactionWorkflowServiceImpleTest {

	private static final String USER = "2a7014a8-9cf0-4d39-bed2-c83a83eddf0c";
	private static final String ACCOUNT = "11111111-2222-3333-4444-555555555555";
	private static final String CANDIDATE = "aaaaaaaa-2222-3333-4444-555555555555";
	private static final String ITEM = "bbbbbbbb-2222-3333-4444-555555555555";

	private IfWealthTransactionDao transactions;
	private IfWealthTransactionWorkflowDao workflow;
	private IfNexusReadService nexus;
	private TransactionWorkflowServiceImple service;

	@BeforeEach
	void setUp() {
		transactions = mock(IfWealthTransactionDao.class);
		workflow = mock(IfWealthTransactionWorkflowDao.class);
		nexus = mock(IfNexusReadService.class);
		service = new TransactionWorkflowServiceImple(transactions, workflow, mock(IfWealthAccountDao.class), nexus);
		RequestHeaderUtil.setUserId(USER);
		when(transactions.findAccountIdsByDigits(USER, "4521")).thenReturn(List.of(ACCOUNT));
		when(transactions.insert(any())).thenAnswer(invocation -> invocation.getArgument(0));
		when(workflow.insert(any())).thenReturn(true);
	}

	@AfterEach
	void tearDown() {
		RequestHeaderUtil.clear();
	}

	private BankLineDto line(String ref, String description, String amount, boolean debit) {
		BankLineDto line = new BankLineDto();
		line.setRef(ref);
		line.setAccountDigits("4521");
		line.setDay("2026-11-20");
		line.setDescription(description);
		line.setAmount(new BigDecimal(amount));
		line.setDebit(debit);
		line.setSure(true);
		return line;
	}

	private WealthTransactionEntity existing(String id, String description) {
		WealthTransactionEntity entity = new WealthTransactionEntity();
		entity.setId(id);
		entity.setDescription(description);
		entity.setKind("expense");
		entity.setAmount(new BigDecimal("99.65"));
		entity.setOccurredOn("2026-11-20");
		return entity;
	}

	@Test
	void aLineWithNoDoubtIsStoredAtOnceAsAnAcceptedTransactionOnItsAccount() {
		BankImportResultDto result = service.importLines("statement", List.of(line("stmt:1", "ORACLE SINGAPORE", "99.65", true)));

		assertEquals(1, result.getStored());
		verify(transactions).insert(argThat(e -> "stmt:1".equals(e.getExternalRef()) && ACCOUNT.equals(e.getAccountId()) && "expense".equals(e.getKind())
				&& "Bank statement".equals(e.getCategory()) && "accepted".equals(e.getReviewStatus()) && new BigDecimal("99.65").equals(e.getAmount())));
		verify(workflow, never()).insert(any());
		verify(nexus).evict("transactions", USER);
	}

	@Test
	void aMoneyInLineIsIncomeAndAnUnplacedAccountStillStoresIt() {
		BankLineDto credit = line("stmt:2", "SALARY", "5000", false);
		credit.setAccountDigits("");

		service.importLines("statement", List.of(credit));

		verify(transactions).findStatementMatches(USER, null, new BigDecimal("5000.00"), "credit", "2026-11-20");
		verify(transactions).insert(argThat(e -> "income".equals(e.getKind()) && e.getAccountId() == null));
	}

	@Test
	void aLineReadBeforeIsNeverStoredOrAskedAgain() {
		when(transactions.referenceExists(USER, "stmt:1")).thenReturn(true);
		when(workflow.exists(USER, "stmt:2")).thenReturn(true);

		BankImportResultDto result = service.importLines("statement", List.of(line("stmt:1", "A", "10", true), line("stmt:2", "B", "10", true)));

		assertEquals(2, result.getRepeated());
		verify(transactions, never()).insert(any());
		verify(workflow, never()).insert(any());
	}

	@Test
	void theSamePaymentAsOneThatIsAlreadyThereIsNotStoredAgain() {
		when(transactions.findStatementMatches(eq(USER), eq(ACCOUNT), any(), eq("debit"), eq("2026-11-20"))).thenReturn(List.of(existing(CANDIDATE, "ORACLE SINGAPORE")));

		BankImportResultDto result = service.importLines("statement", List.of(line("stmt:3", "ORACLE SINGAPORE SG", "99.65", true)));

		assertEquals(1, result.getSame());
		verify(transactions, never()).insert(any());
		verify(workflow, never()).insert(any());
	}

	@Test
	void aLineThatLooksLikeADuplicateGoesToReviewWithItsCandidatesAndIsNotStored() {
		when(transactions.findStatementMatches(any(), any(), any(), any(), any())).thenReturn(List.of(existing(CANDIDATE, "GROCERY MART")));

		BankImportResultDto result = service.importLines("statement", List.of(line("stmt:4", "ORACLE SINGAPORE", "99.65", true)));

		assertEquals(1, result.getReview());
		verify(workflow).insert(argThat(e -> "possible_duplicate".equals(e.getReason()) && CANDIDATE.equals(e.getCandidateIds()) && "statement".equals(e.getSource())
				&& ACCOUNT.equals(e.getAccountId()) && "debit".equals(e.getDirection())));
		verify(transactions, never()).insert(any());

		when(transactions.findStatementMatches(any(), any(), any(), any(), any())).thenReturn(List.of(existing(CANDIDATE, "ORACLE SINGAPORE"), existing("cccccccc-2222-3333-4444-555555555555", "ORACLE SINGAPORE")));
		assertEquals(1, service.importLines("statement", List.of(line("stmt:5", "ORACLE SINGAPORE", "99.65", true))).getReview(), "two candidates are a doubt even when the text is alike");
	}

	@Test
	void aFakeLookingOrUnsureLineGoesToReviewEvenWithNoMatch() {
		BankLineDto fake = line("gmail:m1", "WIN A PRIZE", "5000", false);
		fake.setSuspicious(true);
		BankLineDto unsure = line("stmt:6", "BLURRY", "10", true);
		unsure.setSure(false);

		BankImportResultDto result = service.importLines("alert", List.of(fake, unsure));

		assertEquals(2, result.getReview());
		verify(workflow).insert(argThat(e -> "suspicious".equals(e.getReason()) && "alert".equals(e.getSource())));
		verify(workflow).insert(argThat(e -> "parse_unsure".equals(e.getReason())));
		verify(transactions, never()).insert(any());
	}

	@Test
	void aRaceOnTheReferenceCountsAsRepeatedAndABadLineIsRefused() {
		org.mockito.Mockito.doReturn(null).when(transactions).insert(any());
		assertEquals(1, service.importLines("statement", List.of(line("stmt:7", "A", "10", true))).getRepeated());
		when(workflow.insert(any())).thenReturn(false);
		BankLineDto doubt = line("stmt:8", "B", "10", true);
		doubt.setSure(false);
		assertEquals(1, service.importLines("statement", List.of(doubt)).getRepeated());

		for (var change : List.<java.util.function.Consumer<BankLineDto>>of(l -> l.setRef(" "), l -> l.setDay("2026-02-30"), l -> l.setDay("yesterday"), l -> l.setAmount(BigDecimal.ZERO), l -> l.setAmount(null))) {
			BankLineDto bad = line("stmt:9", "C", "10", true);
			change.accept(bad);
			assertEquals(BaseError.TRANSACTION_INVALID, assertThrows(KarthiException.class, () -> service.importLines("statement", List.of(bad))).getMessage());
		}
	}

	@Test
	void descriptionsAreAlikeWhenTheyShareWordsOrHaveNoneToCompare() {
		assertTrue(TransactionWorkflowServiceImple.similar("ORACLE SINGAPORE", "ORACLE SINGAPORE SG"));
		assertTrue(TransactionWorkflowServiceImple.similar("SWIGGY FOOD", "PAY*SWIGGY FOOD"));
		assertFalse(TransactionWorkflowServiceImple.similar("ORACLE SINGAPORE", "GROCERY MART"));
		assertTrue(TransactionWorkflowServiceImple.similar("", "ANYTHING"));
		assertTrue(TransactionWorkflowServiceImple.similar(null, null));
	}

	private WealthTransactionWorkflowEntity open(String candidates) {
		WealthTransactionWorkflowEntity row = new WealthTransactionWorkflowEntity();
		row.setId(ITEM);
		row.setStatus("open");
		row.setSource("statement");
		row.setSourceRef("stmt:4");
		row.setDescription("ORACLE SINGAPORE");
		row.setAmount(new BigDecimal("99.65"));
		row.setDirection("debit");
		row.setOccurredOn("2026-11-20");
		row.setAccountId(ACCOUNT);
		row.setCandidateIds(candidates);
		return row;
	}

	private WorkflowDecisionRequestDto decision(String decision) {
		WorkflowDecisionRequestDto request = new WorkflowDecisionRequestDto();
		request.setId(ITEM);
		request.setDecision(decision);
		return request;
	}

	@Test
	void theUserMergesAcceptsOrDismissesAnOpenLineOnce() {
		when(workflow.decide(any(), any(), any())).thenReturn(true);
		when(workflow.find(USER, ITEM)).thenReturn(open(CANDIDATE));

		assertTrue(service.decide(decision("merge")));
		verify(workflow).decide(USER, ITEM, "merged");
		verify(transactions, never()).insert(any());

		assertTrue(service.decide(decision("dismiss")));
		verify(workflow).decide(USER, ITEM, "dismissed");

		assertTrue(service.decide(decision("accept")));
		verify(transactions).insert(argThat(e -> "stmt:4".equals(e.getExternalRef()) && "expense".equals(e.getKind()) && ACCOUNT.equals(e.getAccountId()) && "accepted".equals(e.getReviewStatus())));
		verify(workflow).decide(USER, ITEM, "accepted");
		verify(nexus).evict("transactions", USER);
	}

	@Test
	void aMergeNeedsACandidateAndAnUnknownOrDecidedLineOrDecisionIsRefused() {
		when(workflow.find(USER, ITEM)).thenReturn(open(""));
		assertEquals(BaseError.WORKFLOW_DECISION_INVALID, assertThrows(KarthiException.class, () -> service.decide(decision("merge"))).getMessage());
		assertEquals(BaseError.WORKFLOW_DECISION_INVALID, assertThrows(KarthiException.class, () -> service.decide(decision("later"))).getMessage());

		WealthTransactionWorkflowEntity done = open(CANDIDATE);
		done.setStatus("merged");
		when(workflow.find(USER, ITEM)).thenReturn(done);
		assertEquals(BaseError.WORKFLOW_NOT_FOUND, assertThrows(KarthiException.class, () -> service.decide(decision("dismiss"))).getMessage());
		when(workflow.find(USER, ITEM)).thenReturn(null);
		assertEquals(BaseError.WORKFLOW_NOT_FOUND, assertThrows(KarthiException.class, () -> service.decide(decision("dismiss"))).getMessage());

		when(workflow.find(USER, ITEM)).thenReturn(open(CANDIDATE));
		when(workflow.decide(any(), any(), any())).thenReturn(false);
		assertEquals(BaseError.WORKFLOW_NOT_FOUND, assertThrows(KarthiException.class, () -> service.decide(decision("dismiss"))).getMessage(), "decided by someone else meanwhile");

		WorkflowDecisionRequestDto bad = decision("merge");
		bad.setId("x");
		assertEquals(BaseError.INVALID_REQUEST, assertThrows(KarthiException.class, () -> service.decide(bad)).getMessage());
	}

	@Test
	void theOpenLinesAreListedWithTheTransactionsTheyMayCopy() {
		when(workflow.findOpen(USER)).thenReturn(List.of(open(CANDIDATE)));
		when(transactions.findByIds(USER, List.of(CANDIDATE))).thenReturn(List.of(existing(CANDIDATE, "GROCERY MART")));

		var items = service.list();

		assertEquals(1, items.size());
		assertEquals("statement", items.get(0).getSource());
		assertEquals(1, items.get(0).getCandidates().size());
		assertEquals("GROCERY MART", items.get(0).getCandidates().get(0).getDescription());
	}
}
