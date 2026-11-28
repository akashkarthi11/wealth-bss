package com.wealth.bss.serviceimplement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.wealth.bss.common.model.dao.IfWealthPeopleDao;
import com.wealth.bss.common.model.entity.WealthBillSplitShareEntity;
import com.wealth.bss.common.model.entity.WealthPersonEntity;
import com.wealth.bss.common.model.entity.WealthPersonEntryEntity;
import com.wealth.bss.common.model.entity.WealthPersonRollupEntity;
import com.wealth.bss.common.model.entity.WealthTransactionEntity;
import com.wealth.bss.nexus.service.IfNexusReadService;
import com.wealth.common.generic.KarthiException;
import com.wealth.common.model.dto.CachedValueDto;
import com.wealth.common.model.dto.people.PersonEntryRequestDto;
import com.wealth.common.model.dto.people.PersonEntryResultDto;
import com.wealth.common.model.dto.people.RoundOffRequestDto;
import com.wealth.common.model.dto.people.RoundOffResultDto;
import com.wealth.common.model.dto.people.SharePaidRequestDto;
import com.wealth.common.model.dto.people.SharePaidResultDto;
import com.wealth.common.utils.generic.RequestHeaderUtil;

// Adding entries, marking a share paid and a round-off: the rules of the old app (api/_loans.js) with the DAO mocked.
class PeopleEntriesTest {

	private static final String USER = "2a7014a8-9cf0-4d39-bed2-c83a83eddf0c";
	private static final String P1 = "11111111-1111-4111-8111-111111111111";
	private static final String T1 = "21111111-1111-4111-8111-111111111111";
	private static final String T2 = "22222222-2222-4222-8222-222222222222";
	private static final String T3 = "23333333-3333-4333-8333-333333333333";
	private static final String SPLIT = "31111111-1111-4111-8111-111111111111";

	private IfWealthPeopleDao dao;
	private IfNexusReadService nexus;
	private PeopleServiceImple service;

	@BeforeEach
	@SuppressWarnings({ "unchecked", "rawtypes" })
	void setUp() {
		dao = mock(IfWealthPeopleDao.class);
		nexus = mock(IfNexusReadService.class);
		org.mockito.stubbing.Answer<Object> answer = invocation -> {
			CachedValueDto cached = new CachedValueDto();
			cached.setData(((java.util.function.Supplier) invocation.getArgument(2)).get());
			cached.setFetchedAt(Timestamp.valueOf("2026-11-27 10:00:00"));
			return cached;
		};
		when(nexus.read(any(), any(Class.class), any(java.util.function.Supplier.class))).thenAnswer(answer);
		service = new PeopleServiceImple(dao, nexus, Clock.fixed(Instant.parse("2026-11-27T10:00:00Z"), ZoneOffset.UTC));
		RequestHeaderUtil.setUserId(USER);
	}

	@AfterEach
	void tearDown() {
		RequestHeaderUtil.clear();
	}

	private static String code(Runnable call) {
		return assertThrows(KarthiException.class, call::run).getMessage();
	}

	private static WealthTransactionEntity tx(String id, String description, String category, String amount, String day) {
		WealthTransactionEntity transaction = new WealthTransactionEntity();
		transaction.setId(id);
		transaction.setKind("transfer");
		transaction.setDescription(description);
		transaction.setCategory(category);
		transaction.setAmount(new BigDecimal(amount));
		transaction.setDay(day);
		return transaction;
	}

	private static PersonEntryRequestDto link(String name, String... ids) {
		PersonEntryRequestDto request = new PersonEntryRequestDto();
		request.setName(name);
		request.setTransactionIds(List.of(ids));
		return request;
	}

	private static PersonEntryRequestDto cash(String name, String direction, String amount, String on) {
		PersonEntryRequestDto request = new PersonEntryRequestDto();
		request.setName(name);
		request.setDirection(direction);
		request.setAmount(amount == null ? null : new BigDecimal(amount));
		request.setOccurredOn(on);
		return request;
	}

	// ---- entries ----

	@Test
	@SuppressWarnings("unchecked")
	void linkingTransfersAddsThePersonWithEachDirectionAndKeepsOtherNamesAsAliases() {
		when(dao.findTransfersForEntries(eq(USER), any())).thenReturn(List.of(tx(T1, "UPI transfer to Naveen K", "Transfer sent", "22000.00", "2026-01-03"),
				tx(T2, "Mr Naveen K · UPI · ref 7", "Transfer received", "400.00", "2026-02-01"), tx(T3, "UPI transfer to Naveen Krishnan", "Transfer sent", "50.00", "2026-03-01")));
		when(dao.addEntries(eq(USER), any(), any(), any())).thenReturn(P1);

		PersonEntryResultDto result = service.createEntries(link("  Naveen   K ", T1, T2, T3));

		assertTrue(result.isStored());
		assertFalse(result.isDryRun());
		assertEquals(P1, result.getPersonId());
		assertEquals(3, result.getEntries());
		ArgumentCaptor<WealthPersonEntity> who = ArgumentCaptor.forClass(WealthPersonEntity.class);
		ArgumentCaptor<List<WealthPersonEntryEntity>> entries = ArgumentCaptor.forClass(List.class);
		ArgumentCaptor<List<String>> aliases = ArgumentCaptor.forClass(List.class);
		verify(dao).addEntries(eq(USER), who.capture(), entries.capture(), aliases.capture());
		assertEquals("Naveen K", who.getValue().getName(), "extra spaces are squeezed");
		assertEquals(List.of("out", "in", "out"), entries.getValue().stream().map(WealthPersonEntryEntity::getDirection).toList());
		assertEquals(new BigDecimal("22000.00"), entries.getValue().get(0).getAmount());
		assertEquals("2026-02-01", entries.getValue().get(1).getOn());
		assertEquals(T2, entries.getValue().get(1).getTransactionId());
		assertEquals(List.of("Naveen Krishnan"), aliases.getValue(), "the person's own name is not an alias");
		verify(nexus).evict("people", USER);
		verify(nexus).evict("transactions", USER);
	}

	@Test
	void aSingleTransferMayForceItsDirection() {
		when(dao.findTransfersForEntries(eq(USER), any())).thenReturn(List.of(tx(T1, "UPI transfer to Dev", "Transfer received", "900.00", "2026-05-05")));
		when(dao.addEntries(eq(USER), any(), any(), any())).thenReturn(P1);
		PersonEntryRequestDto request = link("Dev", T1);
		request.setDirection("out");

		service.createEntries(request);

		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<WealthPersonEntryEntity>> entries = ArgumentCaptor.forClass(List.class);
		verify(dao).addEntries(eq(USER), any(), entries.capture(), any());
		assertEquals("out", entries.getValue().get(0).getDirection(), "received, but forced to lent");
	}

	@Test
	void aTransferThatIsMissingNotATransferLinkedOrSplitIsRefusedAndNothingIsWritten() {
		when(dao.findTransfersForEntries(eq(USER), any())).thenReturn(List.of());
		assertEquals("6055", code(() -> service.createEntries(link("Dev", T1))), "not the caller's or not found");

		WealthTransactionEntity bill = tx(T1, "Dinner", "Dining", "500.00", "2026-05-05");
		bill.setKind("expense");
		when(dao.findTransfersForEntries(eq(USER), any())).thenReturn(List.of(bill));
		assertEquals("6055", code(() -> service.createEntries(link("Dev", T1))), "only transfers");

		WealthTransactionEntity linked = tx(T1, "UPI transfer to Dev", "Transfer sent", "500.00", "2026-05-05");
		linked.setSplitStatus("linked");
		when(dao.findTransfersForEntries(eq(USER), any())).thenReturn(List.of(linked));
		assertEquals("6056", code(() -> service.createEntries(link("Dev", T1))), "already linked to a person");
		linked.setSplitStatus("active");
		assertEquals("6056", code(() -> service.createEntries(link("Dev", T1))), "split among people");

		verify(dao, never()).addEntries(any(), any(), any(), any());
	}

	@Test
	void aDryRunWritesNothingAndSaysWhatWouldBeStored() {
		when(dao.findTransfersForEntries(eq(USER), any())).thenReturn(List.of(tx(T1, "UPI transfer to Dev", "Transfer sent", "900.00", "2026-05-05")));
		PersonEntryRequestDto request = link("Dev", T1);
		request.setDryRun(true);

		PersonEntryResultDto result = service.createEntries(request);

		assertTrue(result.isDryRun());
		assertFalse(result.isStored());
		assertNull(result.getPersonId());
		assertEquals(1, result.getEntries());
		assertEquals("Dev", result.getPerson());
		verify(dao, never()).addEntries(any(), any(), any(), any());
		verify(nexus, never()).evict(any(), any());
	}

	@Test
	@SuppressWarnings("unchecked")
	void anAmountByHandNeedsADirectionAnAmountAndADateThatIsNotInTheFuture() {
		when(dao.addEntries(eq(USER), any(), any(), any())).thenReturn(P1);
		service.createEntries(cash("Asha", "out", "1250.5", null));
		ArgumentCaptor<List<WealthPersonEntryEntity>> entries = ArgumentCaptor.forClass(List.class);
		verify(dao).addEntries(eq(USER), any(), entries.capture(), any());
		assertEquals(new BigDecimal("1250.50"), entries.getValue().get(0).getAmount());
		assertEquals("2026-11-27", entries.getValue().get(0).getOn(), "no date means today");
		assertNull(entries.getValue().get(0).getTransactionId());
		verify(nexus).evict("people", USER);

		assertEquals("6057", code(() -> service.createEntries(cash("Asha", "sideways", "10", null))));
		assertEquals("6057", code(() -> service.createEntries(cash("Asha", "out", null, null))));
		assertEquals("6057", code(() -> service.createEntries(cash("Asha", "in", "0", null))));
		assertEquals("6057", code(() -> service.createEntries(cash("Asha", "in", "-5", null))));
		assertEquals("6062", code(() -> service.createEntries(cash("Asha", "in", "10", "2026-11-28"))), "tomorrow");
		assertEquals("6062", code(() -> service.createEntries(cash("Asha", "in", "10", "yesterday"))));
		service.createEntries(cash("Asha", "in", "10", "2026-11-27"));
	}

	@Test
	void aBadRequestOrAnUnknownPersonIsRefused() {
		assertEquals("611", code(() -> service.createEntries(null)));
		assertEquals("611", code(() -> service.createEntries(cash("   ", "out", "10", null))), "a name is needed");
		assertEquals("611", code(() -> service.createEntries(link("Dev", "not-an-id"))));
		assertEquals("611", code(() -> service.createEntries(link("Dev", T1, T1))), "duplicate transaction ids");
		PersonEntryRequestDto byId = cash(null, "out", "10", null);
		byId.setPersonId("not-an-id");
		assertEquals("611", code(() -> service.createEntries(byId)));
		byId.setPersonId(P1);
		assertEquals("6054", code(() -> service.createEntries(byId)), "not the caller's person");
		WealthPersonEntity removed = new WealthPersonEntity();
		removed.setId(P1);
		removed.setName("Gone");
		removed.setStatus("removed");
		when(dao.findPerson(USER, P1)).thenReturn(removed);
		assertEquals("6054", code(() -> service.createEntries(byId)), "a removed person");
		verify(dao, never()).addEntries(any(), any(), any(), any());
	}

	// ---- a paid share ----

	private static SharePaidRequestDto paid(String on) {
		SharePaidRequestDto request = new SharePaidRequestDto();
		request.setSplitId(SPLIT);
		request.setPersonId(P1);
		request.setOccurredOn(on);
		return request;
	}

	private static WealthBillSplitShareEntity shareOf(String amount, String rollupId) {
		WealthBillSplitShareEntity share = new WealthBillSplitShareEntity();
		share.setAmount(new BigDecimal(amount));
		share.setRollupId(rollupId);
		return share;
	}

	@Test
	void aShareIsMarkedPaidOnceAndTheCacheIsCleared() {
		when(dao.findShareToPay(USER, SPLIT, P1)).thenReturn(shareOf("393.34", null));
		when(dao.addSharePaid(eq(USER), eq(P1), eq(SPLIT), any(), any(), any())).thenReturn(true);

		SharePaidResultDto result = service.markSharePaid(paid(null));

		assertTrue(result.isStored());
		assertEquals(new BigDecimal("393.34"), result.getAmount());
		assertEquals("2026-11-27", result.getOn());
		verify(dao).addSharePaid(USER, P1, SPLIT, new BigDecimal("393.34"), "2026-11-27", null);
		verify(nexus).evict("people", USER);
	}

	@Test
	void aSharePaidBeforeStoresNothingAndIsNotAnError() {
		when(dao.findShareToPay(USER, SPLIT, P1)).thenReturn(shareOf("100.00", null));
		when(dao.sharePaid(P1, SPLIT)).thenReturn(true);

		SharePaidResultDto result = service.markSharePaid(paid(null));

		assertFalse(result.isStored());
		assertEquals("paid", result.getAlready());
		verify(dao, never()).addSharePaid(any(), any(), any(), any(), any(), any());
	}

	@Test
	void aShareThatIsMissingClosedByARoundOffOrEmptyIsRefused() {
		assertEquals("6058", code(() -> service.markSharePaid(paid(null))), "not found");
		when(dao.findShareToPay(USER, SPLIT, P1)).thenReturn(shareOf("100.00", "rollup-1"));
		assertEquals("6059", code(() -> service.markSharePaid(paid(null))), "closed by a round-off");
		when(dao.findShareToPay(USER, SPLIT, P1)).thenReturn(shareOf("0.00", null));
		assertEquals("6059", code(() -> service.markSharePaid(paid(null))), "nothing to pay");
		when(dao.findShareToPay(USER, SPLIT, P1)).thenReturn(shareOf("100.00", null));
		assertEquals("6062", code(() -> service.markSharePaid(paid("2026-12-01"))), "future date");
		SharePaidRequestDto bad = paid(null);
		bad.setSplitId("x");
		assertEquals("611", code(() -> service.markSharePaid(bad)));
		verify(dao, never()).addSharePaid(any(), any(), any(), any(), any(), any());
	}

	@Test
	void aDryRunOfAPaidShareStoresNothing() {
		when(dao.findShareToPay(USER, SPLIT, P1)).thenReturn(shareOf("100.00", null));
		SharePaidRequestDto request = paid(null);
		request.setDryRun(true);

		SharePaidResultDto result = service.markSharePaid(request);

		assertTrue(result.isDryRun());
		assertFalse(result.isStored());
		assertEquals(new BigDecimal("100.00"), result.getAmount());
		verify(dao, never()).addSharePaid(any(), any(), any(), any(), any(), any());
	}

	// ---- a round-off ----

	private void personWithLentAndBack(String status) {
		WealthPersonEntity person = new WealthPersonEntity();
		person.setId(P1);
		person.setName("Dev");
		person.setStatus("open");
		when(dao.findPeople(any())).thenReturn(List.of(person));
		WealthPersonEntryEntity lent = new WealthPersonEntryEntity();
		lent.setId("e1");
		lent.setPersonId(P1);
		lent.setDirection("out");
		lent.setAmount(new BigDecimal("1000.00"));
		lent.setOn("2026-01-01");
		lent.setStatus(status);
		WealthPersonEntryEntity back = new WealthPersonEntryEntity();
		back.setId("e2");
		back.setPersonId(P1);
		back.setDirection("in");
		back.setAmount(new BigDecimal("300.00"));
		back.setOn("2026-02-01");
		back.setStatus("active");
		when(dao.findEntries(any())).thenReturn(List.of(lent, back));
	}

	private static RoundOffRequestDto round(String back, String carried) {
		RoundOffRequestDto request = new RoundOffRequestDto();
		request.setPersonId(P1);
		request.setAlreadyBack(back == null ? null : new BigDecimal(back));
		request.setNewAmount(carried == null ? null : new BigDecimal(carried));
		return request;
	}

	@Test
	void aRoundOffClosesTheOpenLinesAndCarriesWhatIsLeftForward() {
		personWithLentAndBack("active");
		when(dao.roundOff(eq(USER), any(), any())).thenAnswer(invocation -> {
			((WealthPersonRollupEntity) invocation.getArgument(1)).setId("roll-1");
			return 2;
		});

		RoundOffResultDto result = service.roundOff(round("100", null));

		assertTrue(result.isStored());
		assertEquals("roll-1", result.getRollupId());
		assertEquals(2, result.getClosed());
		assertEquals(2, result.getClosedItems());
		assertEquals(new BigDecimal("700.00"), result.getTotalOwed());
		assertEquals(new BigDecimal("100.00"), result.getAlreadyBack());
		assertEquals(new BigDecimal("600.00"), result.getNewAmount(), "owed minus already back when no new amount is given");
		assertEquals(new BigDecimal("0.00"), result.getRoundedBy());
		assertEquals("2026-11-27", result.getClosedOn());
		ArgumentCaptor<WealthPersonRollupEntity> rollup = ArgumentCaptor.forClass(WealthPersonRollupEntity.class);
		ArgumentCaptor<String> note = ArgumentCaptor.forClass(String.class);
		verify(dao).roundOff(eq(USER), rollup.capture(), note.capture());
		assertEquals(P1, rollup.getValue().getPersonId());
		assertEquals(new BigDecimal("700.00"), rollup.getValue().getTotalOwed());
		assertEquals(2, rollup.getValue().getClosedItems());
		assertEquals("Round-off 2026-11-27: 2 entries, owed ₹700, already back ₹100", note.getValue());
		verify(nexus).evict("people", USER);
	}

	@Test
	void aNewAmountBelowWhatIsLeftMeansTheDifferenceIsLetGo() {
		personWithLentAndBack("active");

		RoundOffRequestDto request = round("100", "500");
		request.setDryRun(true);
		RoundOffResultDto result = service.roundOff(request);

		assertTrue(result.isDryRun());
		assertFalse(result.isStored());
		assertEquals(new BigDecimal("100.00"), result.getRoundedBy());
		verify(dao, never()).roundOff(any(), any(), any());
		verify(nexus, never()).evict(any(), any());
	}

	@Test
	void aRoundOffIsRefusedForNothingOpenAnUnknownPersonAnAmountBelowZeroOrABadDate() {
		assertEquals("6054", code(() -> service.roundOff(round(null, null))), "no such person");
		personWithLentAndBack("written_off");
		WealthPersonEntryEntity backOnly = new WealthPersonEntryEntity();
		backOnly.setId("e3");
		backOnly.setPersonId(P1);
		backOnly.setDirection("in");
		backOnly.setAmount(new BigDecimal("1.00"));
		backOnly.setOn("2026-03-01");
		backOnly.setStatus("written_off");
		when(dao.findEntries(any())).thenReturn(List.of(backOnly));
		assertEquals("6060", code(() -> service.roundOff(round(null, null))), "every line is written off");

		personWithLentAndBack("active");
		assertEquals("6061", code(() -> service.roundOff(round("-1", null))));
		assertEquals("6061", code(() -> service.roundOff(round("0", "-5"))));
		RoundOffRequestDto future = round(null, null);
		future.setClosedOn("2026-12-31");
		assertEquals("6062", code(() -> service.roundOff(future)));
		RoundOffRequestDto bad = round(null, null);
		bad.setPersonId("x");
		assertEquals("611", code(() -> service.roundOff(bad)));
		verify(dao, never()).roundOff(any(), any(), any());
	}
}
