package com.wealth.bss.serviceimplement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.wealth.bss.common.model.dao.IfWealthPeopleDao;
import com.wealth.bss.common.model.entity.WealthLendRequestEntity;
import com.wealth.bss.common.model.entity.WealthPersonEntity;
import com.wealth.bss.common.model.entity.WealthPersonEntryEntity;
import com.wealth.bss.common.utils.security.LoginRateLimiter;
import com.wealth.bss.nexus.service.IfNexusReadService;
import com.wealth.common.generic.KarthiException;
import com.wealth.common.model.dto.people.CommentCreateRequestDto;
import com.wealth.common.model.dto.people.EntryNoteRequestDto;
import com.wealth.common.model.dto.people.EntryNoteResultDto;
import com.wealth.common.model.dto.people.LendPaidRequestDto;
import com.wealth.common.model.dto.people.LendPaidResultDto;
import com.wealth.common.model.dto.people.LendRemarkRequestDto;
import com.wealth.common.model.dto.people.RequestDecideRequestDto;
import com.wealth.common.model.dto.people.RequestDecideResultDto;
import com.wealth.common.utils.generic.RequestHeaderUtil;

// "I've paid" and remarks from the person (public page) and the owner's notes, answers and decisions: the old app's rules (api/_lendshare.js, api/_loans.js) with the DAO mocked.
class PeoplePaidTest {

	private static final String USER = "2a7014a8-9cf0-4d39-bed2-c83a83eddf0c";
	private static final String P1 = "11111111-1111-4111-8111-111111111111";
	private static final String ENTRY = "41111111-1111-4111-8111-111111111111";
	private static final String SHARE = "51111111-1111-4111-8111-111111111111";
	private static final String REQ = "61111111-1111-4111-8111-111111111111";
	private static final String TOKEN = "abcdefghijklmnopqrstuvwxyz012345";

	private IfWealthPeopleDao dao;
	private IfNexusReadService nexus;
	private LoginRateLimiter limiter;
	private PeopleServiceImple service;

	@BeforeEach
	void setUp() {
		dao = mock(IfWealthPeopleDao.class);
		nexus = mock(IfNexusReadService.class);
		limiter = mock(LoginRateLimiter.class);
		when(limiter.attempt(any(), anyLong(), anyLong())).thenReturn(true);
		service = new PeopleServiceImple(dao, nexus, Clock.fixed(Instant.parse("2026-11-27T10:00:00Z"), ZoneOffset.UTC), limiter);
		RequestHeaderUtil.setIp("203.0.113.5");
	}

	@AfterEach
	void tearDown() {
		RequestHeaderUtil.clear();
	}

	private static String code(Runnable call) {
		return assertThrows(KarthiException.class, call::run).getMessage();
	}

	private void sharedPerson() {
		WealthPersonEntity owner = new WealthPersonEntity();
		owner.setId(P1);
		owner.setUserId(USER);
		owner.setName("Dev");
		when(dao.findPersonByShareHash(PeopleServiceImple.hashOf(TOKEN))).thenReturn(owner);
	}

	private static LendPaidRequestDto paid(String amount, String on, String method) {
		LendPaidRequestDto request = new LendPaidRequestDto();
		request.setToken(TOKEN);
		request.setAmount(amount == null ? null : new BigDecimal(amount));
		request.setPaidOn(on);
		request.setMethod(method);
		return request;
	}

	private static LendRemarkRequestDto remark(String entryId, String shareId, String body) {
		LendRemarkRequestDto request = new LendRemarkRequestDto();
		request.setToken(TOKEN);
		request.setEntryId(entryId);
		request.setShareId(shareId);
		request.setBody(body);
		return request;
	}

	// ---- "I've paid" from the person ----

	@Test
	void aPaymentMessageWaitsForTheOwnerWithTheDefaultsAndTheOwnersCacheIsCleared() {
		sharedPerson();

		LendPaidResultDto result = service.lendPaid(paid("1500.5", null, null));

		assertTrue(result.isStored());
		verify(dao).addLendRequest(P1, new BigDecimal("1500.50"), "2026-11-27", "upi", null);
		verify(nexus).evict("people", USER);
	}

	@Test
	void theAmountTheDayTheMethodAndTheNoteAreChecked() {
		sharedPerson();
		assertEquals("6057", code(() -> service.lendPaid(paid(null, null, null))));
		assertEquals("6057", code(() -> service.lendPaid(paid("0", null, null))));
		assertEquals("6057", code(() -> service.lendPaid(paid("1000000001", null, null))), "more than the limit");
		assertEquals("6062", code(() -> service.lendPaid(paid("10", "2026-11-28", null))), "tomorrow");
		assertEquals("6062", code(() -> service.lendPaid(paid("10", "1999-12-31", null))), "before 2000");
		assertEquals("611", code(() -> service.lendPaid(paid("10", null, "carrier pigeon"))));
		LendPaidRequestDto noted = paid("10", "2026-11-20", "bank");
		noted.setNote("x".repeat(500));
		service.lendPaid(noted);
		ArgumentCaptor<String> note = ArgumentCaptor.forClass(String.class);
		verify(dao).addLendRequest(eq(P1), any(), eq("2026-11-20"), eq("bank"), note.capture());
		assertEquals(200, note.getValue().length(), "the note is cut to 200");
	}

	@Test
	void atMostThreeMessagesWaitAndEveryAddressIsLimited() {
		sharedPerson();
		when(dao.countPendingRequests(P1)).thenReturn(3);
		assertEquals("6071", code(() -> service.lendPaid(paid("10", null, null))));
		verify(dao, never()).addLendRequest(any(), any(), any(), any(), any());

		when(limiter.attempt(eq("lendpaid:203.0.113.5"), anyLong(), anyLong())).thenReturn(false);
		assertEquals("6069", code(() -> service.lendPaid(paid("10", null, null))));
	}

	@Test
	void aWrongSecretCannotSendAMessage() {
		LendPaidRequestDto bad = paid("10", null, null);
		bad.setToken("short");
		assertEquals("6068", code(() -> service.lendPaid(bad)));
		assertEquals("6068", code(() -> service.lendPaid(paid("10", null, null))), "no person has this secret");
		assertEquals("6068", code(() -> service.lendPaid(null)));
		verify(dao, never()).addLendRequest(any(), any(), any(), any(), any());
	}

	// ---- a remark from the person ----

	@Test
	void aRemarkOnTheirOwnLineIsStoredAsTheirsAndTheOwnerIsToldByTheCache() {
		sharedPerson();
		when(dao.entryOfPerson(ENTRY, P1)).thenReturn(true);

		assertTrue(service.lendRemark(remark(ENTRY, null, "  paid\u0001half  ")).isStored());

		verify(dao).addRemark(P1, ENTRY, null, "person", "paid half", "lend-link");
		verify(nexus).evict("people", USER);
	}

	@Test
	void aRemarkOnABillShareOfTheirsIsStoredToo() {
		sharedPerson();
		when(dao.shareOfPerson(SHARE, P1)).thenReturn(true);

		service.lendRemark(remark(null, SHARE, "ok"));

		verify(dao).addRemark(P1, null, SHARE, "person", "ok", "lend-link");
	}

	@Test
	void aRemarkOnSomeoneElsesLineOrWithABadShapeOrTooOftenIsRefused() {
		sharedPerson();
		assertEquals("6063", code(() -> service.lendRemark(remark(ENTRY, null, "hi"))), "not their line");
		assertEquals("6058", code(() -> service.lendRemark(remark(null, SHARE, "hi"))), "not their share");
		assertEquals("611", code(() -> service.lendRemark(remark(null, null, "hi"))), "a row is needed");
		assertEquals("611", code(() -> service.lendRemark(remark(ENTRY, SHARE, "hi"))), "one row, not two");
		assertEquals("611", code(() -> service.lendRemark(remark(ENTRY, null, "   "))), "an empty remark");
		assertEquals("611", code(() -> service.lendRemark(remark("x", null, "hi"))));
		when(dao.entryOfPerson(ENTRY, P1)).thenReturn(true);
		when(dao.countPersonRemarksSince(eq(P1), any())).thenReturn(20);
		assertEquals("6072", code(() -> service.lendRemark(remark(ENTRY, null, "hi"))), "20 a day");
		verify(dao, never()).addRemark(any(), any(), any(), any(), any(), any());
	}

	// ---- the owner: answer, description ----

	@Test
	void theOwnerAnswersARemarkOnTheirOwnLine() {
		RequestHeaderUtil.setUserId(USER);
		when(dao.findEntryPersonOfOwner(USER, ENTRY)).thenReturn(P1);
		when(dao.addRemark(P1, ENTRY, null, "owner", "thanks", "owner")).thenReturn("c1");
		CommentCreateRequestDto request = new CommentCreateRequestDto();
		request.setEntryId(ENTRY);
		request.setBody("thanks");

		assertEquals("c1", service.createComment(request).getCommentId());

		verify(nexus).evict("people", USER);
		request.setEntryId("41111111-1111-4111-8111-999999999999");
		assertEquals("6063", code(() -> service.createComment(request)), "not their line");
		CommentCreateRequestDto empty = new CommentCreateRequestDto();
		empty.setShareId(SHARE);
		empty.setBody("");
		assertEquals("611", code(() -> service.createComment(empty)));
	}

	@Test
	void theOwnerDescribesALineAndAnEmptyDescriptionClearsIt() {
		RequestHeaderUtil.setUserId(USER);
		when(dao.setEntryNote(eq(USER), eq(ENTRY), any())).thenReturn(true);
		EntryNoteRequestDto request = new EntryNoteRequestDto();
		request.setEntryId(ENTRY);
		request.setNote("for the cab");

		EntryNoteResultDto result = service.updateEntryNote(request);

		assertEquals("for the cab", result.getNote());
		verify(dao).setEntryNote(USER, ENTRY, "for the cab");
		request.setNote("   ");
		assertNull(service.updateEntryNote(request).getNote());
		verify(dao).setEntryNote(USER, ENTRY, null);
		request.setNote("x".repeat(500));
		assertEquals(200, service.updateEntryNote(request).getNote().length());
		when(dao.setEntryNote(eq(USER), eq(ENTRY), any())).thenReturn(false);
		assertEquals("6063", code(() -> service.updateEntryNote(request)));
	}

	// ---- the owner: accept or decline ----

	private void waitingRequest(String status) {
		WealthLendRequestEntity waiting = new WealthLendRequestEntity();
		waiting.setId(REQ);
		waiting.setPersonId(P1);
		waiting.setName("Dev");
		waiting.setAmount(new BigDecimal("1000.00"));
		waiting.setPaidOn("2026-11-25");
		waiting.setMethod("upi");
		waiting.setNote("first part");
		waiting.setStatus(status);
		when(dao.findRequestOfOwner(USER, REQ)).thenReturn(waiting);
	}

	private void owing(String lent) {
		WealthPersonEntity person = new WealthPersonEntity();
		person.setId(P1);
		person.setName("Dev");
		person.setStatus("open");
		when(dao.findPeople(any())).thenReturn(List.of(person));
		WealthPersonEntryEntity entry = new WealthPersonEntryEntity();
		entry.setId("e1");
		entry.setPersonId(P1);
		entry.setDirection("out");
		entry.setAmount(new BigDecimal(lent));
		entry.setOn("2026-10-01");
		entry.setStatus("active");
		when(dao.findEntries(any())).thenReturn(List.of(entry));
	}

	private static RequestDecideRequestDto decide(String decision) {
		RequestDecideRequestDto request = new RequestDecideRequestDto();
		request.setRequestId(REQ);
		request.setDecision(decision);
		return request;
	}

	@Test
	void acceptingAMessageThatCoversWhatIsOwedAddsACashLineAndSettlesThePerson() {
		RequestHeaderUtil.setUserId(USER);
		waitingRequest("pending");
		owing("1000.00");
		when(dao.acceptRequest(eq(USER), any(), any(), any(), anyBoolean(), any())).thenReturn(true);

		RequestDecideResultDto result = service.decideRequest(decide("accept"));

		assertTrue(result.isStored());
		assertEquals(new BigDecimal("1000.00"), result.getAmount());
		assertEquals("2026-11-25", result.getPaidOn());
		assertTrue(result.isCloseLend(), "it covers what is owed");
		verify(dao).acceptRequest(eq(USER), any(), eq(new BigDecimal("1000.00")), eq("2026-11-25"), eq(true), eq("Paid back via shared link (upi: first part)"));
		verify(nexus).evict("people", USER);
	}

	@Test
	void aPartPaymentOrAChosenAmountKeepsThePersonOpenUnlessTheOwnerSaysOtherwise() {
		RequestHeaderUtil.setUserId(USER);
		waitingRequest("pending");
		owing("5000.00");
		when(dao.acceptRequest(eq(USER), any(), any(), any(), anyBoolean(), any())).thenReturn(true);

		assertFalse(service.decideRequest(decide("accept")).isCloseLend(), "1000 of 5000 owed");

		RequestDecideRequestDto lower = decide("accept");
		lower.setAmount(new BigDecimal("750"));
		lower.setPaidOn("2026-11-20");
		lower.setCloseLend(true);
		RequestDecideResultDto result = service.decideRequest(lower);

		assertEquals(new BigDecimal("750.00"), result.getAmount());
		assertTrue(result.isCloseLend(), "the owner chose to close it");
		verify(dao).acceptRequest(eq(USER), any(), eq(new BigDecimal("750.00")), eq("2026-11-20"), eq(true), any());
	}

	@Test
	void decliningAMessageChangesNothingElse() {
		RequestHeaderUtil.setUserId(USER);
		waitingRequest("pending");
		when(dao.declineRequest(USER, REQ)).thenReturn(true);

		RequestDecideResultDto result = service.decideRequest(decide("decline"));

		assertTrue(result.isStored());
		assertEquals("decline", result.getDecision());
		verify(dao, never()).acceptRequest(any(), any(), any(), any(), anyBoolean(), any());
		verify(nexus).evict("people", USER);
	}

	@Test
	void aMessageThatIsMissingDecidedAlreadyOrWithABadAmountOrDayIsRefused() {
		RequestHeaderUtil.setUserId(USER);
		assertEquals("6073", code(() -> service.decideRequest(decide("accept"))), "not the caller's");
		waitingRequest("accepted");
		assertEquals("6074", code(() -> service.decideRequest(decide("accept"))), "decided already");
		waitingRequest("pending");
		owing("1000.00");
		RequestDecideRequestDto zero = decide("accept");
		zero.setAmount(BigDecimal.ZERO);
		assertEquals("6057", code(() -> service.decideRequest(zero)));
		RequestDecideRequestDto future = decide("accept");
		future.setPaidOn("2026-12-31");
		assertEquals("6062", code(() -> service.decideRequest(future)));
		assertEquals("611", code(() -> service.decideRequest(decide("maybe"))));
		assertEquals("611", code(() -> service.decideRequest(null)));
		when(dao.acceptRequest(eq(USER), any(), any(), any(), anyBoolean(), any())).thenReturn(false);
		assertEquals("6074", code(() -> service.decideRequest(decide("accept"))), "decided a moment ago by another");
	}
}
