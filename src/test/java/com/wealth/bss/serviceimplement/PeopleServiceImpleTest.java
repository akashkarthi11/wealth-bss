package com.wealth.bss.serviceimplement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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

import com.wealth.bss.common.model.dao.IfWealthPeopleDao;
import com.wealth.bss.common.model.entity.WealthBillSplitEntity;
import com.wealth.bss.common.model.entity.WealthBillSplitShareEntity;
import com.wealth.bss.common.model.entity.WealthLendCommentEntity;
import com.wealth.bss.common.model.entity.WealthLendRequestEntity;
import com.wealth.bss.common.model.entity.WealthPersonAliasEntity;
import com.wealth.bss.common.model.entity.WealthPersonEntity;
import com.wealth.bss.common.model.entity.WealthPersonEntryEntity;
import com.wealth.bss.common.model.entity.WealthPersonRollupEntity;
import com.wealth.bss.common.model.entity.WealthTransactionEntity;
import com.wealth.bss.nexus.service.IfNexusReadService;
import com.wealth.common.model.dto.CachedValueDto;
import com.wealth.common.model.dto.people.PeopleOverviewDto;
import com.wealth.common.model.dto.people.PersonDto;
import com.wealth.common.utils.generic.RequestHeaderUtil;

class PeopleServiceImpleTest {

	private static final String TODAY = "2026-11-27";

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
		when(nexus.hardRefresh(any(), any(Class.class), any(java.util.function.Supplier.class))).thenAnswer(answer);
		service = new PeopleServiceImple(dao, nexus, Clock.fixed(Instant.parse("2026-11-27T10:00:00Z"), ZoneOffset.UTC));
		RequestHeaderUtil.setUserId("2a7014a8-9cf0-4d39-bed2-c83a83eddf0c");
	}

	@AfterEach
	void tearDown() {
		RequestHeaderUtil.clear();
	}

	private static PeopleServiceImple.Move move(String on, double amount, String direction) {
		return new PeopleServiceImple.Move(on, amount, direction);
	}

	// The expected numbers come from running the old app's personFigures on the same moves.
	@Test
	void theFiguresMatchTheOldAppWithInterestRepaymentOrderAndOverpayment() {
		PeopleServiceImple.Figures a = PeopleServiceImple.personFigures(
				List.of(move("2026-05-01", 500, "out"), move("2026-01-01", 10000, "out"), move("2026-03-01", 2000, "in")), new BigDecimal("0.12"), "2026-11-01", "open", TODAY);
		assertEquals(10500, a.lent());
		assertEquals(2000, a.repaid());
		assertEquals(764.57, a.interest());
		assertEquals(9458.54, a.owed());
		assertEquals("2026-05-01", a.lastMovement());
		assertTrue(a.overdue());

		PeopleServiceImple.Figures b = PeopleServiceImple.personFigures(List.of(move("2026-02-10", 300, "out"), move("2026-02-20", 450, "in")), null, null, "open", TODAY);
		assertEquals(0, b.owed());
		assertEquals(150, b.overpaid(), "paid back more than lent");
		assertFalse(b.overdue());

		PeopleServiceImple.Figures c = PeopleServiceImple.personFigures(List.of(move("2026-06-01", 1000, "out")), new BigDecimal("0.1"), "2026-12-31", "settled", TODAY);
		assertEquals(0, c.owed(), "a settled person owes nothing");
		assertEquals(1000, c.lent());

		PeopleServiceImple.Figures none = PeopleServiceImple.personFigures(List.of(), null, null, "open", TODAY);
		assertNull(none.lastMovement());
	}

	@Test
	void aNameIsTheOneInBankTextWithoutTitlesOrSourceNotes() {
		assertEquals("Arun S", PeopleServiceImple.personOf("UPI transfer to Arun S"));
		assertEquals("Arun S", PeopleServiceImple.personOf("Mr Arun S · UPI · HDFC statement · ref 1"));
		assertEquals("Ravi K", PeopleServiceImple.personOf("paid to  Mrs.  Ravi   K"));
		assertEquals("", PeopleServiceImple.personOf(null));
	}

	private WealthPersonEntity person(String id, String name, String rate, String dueOn, boolean hidden) {
		WealthPersonEntity person = new WealthPersonEntity();
		person.setId(id);
		person.setName(name);
		person.setKind("person");
		person.setStatus("open");
		person.setAnnualRate(rate == null ? null : new BigDecimal(rate));
		person.setDueOn(dueOn);
		person.setHidden(hidden);
		return person;
	}

	private WealthPersonEntryEntity entry(String id, String personId, String direction, String amount, String on, String status) {
		WealthPersonEntryEntity entry = new WealthPersonEntryEntity();
		entry.setId(id);
		entry.setPersonId(personId);
		entry.setDirection(direction);
		entry.setAmount(new BigDecimal(amount));
		entry.setOn(on);
		entry.setStatus(status);
		return entry;
	}

	private WealthBillSplitShareEntity share(String id, String splitId, String personId, String base, String tax, String amount) {
		WealthBillSplitShareEntity share = new WealthBillSplitShareEntity();
		share.setId(id);
		share.setSplitId(splitId);
		share.setPersonId(personId);
		share.setBase(new BigDecimal(base));
		share.setTax(new BigDecimal(tax));
		share.setAmount(new BigDecimal(amount));
		share.setStatus("active");
		return share;
	}

	private WealthTransactionEntity transfer(String id, String description, String category, String amount, String day) {
		WealthTransactionEntity transfer = new WealthTransactionEntity();
		transfer.setId(id);
		transfer.setDescription(description);
		transfer.setCategory(category);
		transfer.setAmount(new BigDecimal(amount));
		transfer.setDay(day);
		return transfer;
	}

	private PersonDto find(PeopleOverviewDto dto, String id) {
		return dto.getPeople().stream().filter(p -> p.getId().equals(id)).findFirst().orElseThrow();
	}

	@Test
	void theOverviewAddsUpEachPersonAndLeavesHiddenPeopleOutOfTheTotals() {
		when(dao.findPeople(any())).thenReturn(List.of(person("a", "Arun S", "0.12", "2026-11-01", false), person("b", "Bina", null, null, false), person("h", "Hidden Hari", null, null, true)));
		WealthPersonEntryEntity lent = entry("e1", "a", "out", "10000.00", "2026-01-01", "active");
		WealthPersonEntryEntity back = entry("e2", "a", "in", "2000.00", "2026-03-01", "active");
		back.setTransactionId("t1");
		back.setTxDescription("UPI transfer to Arun S");
		back.setAccount("HDFC");
		WealthPersonEntryEntity off = entry("e4", "a", "out", "500.00", "2026-05-01", "written_off");
		WealthPersonEntryEntity closed = entry("e5", "a", "out", "777.00", "2026-02-01", "active");
		closed.setRollupId("r1");
		when(dao.findEntries(any())).thenReturn(List.of(off, back, closed, lent));
		WealthBillSplitEntity split = new WealthBillSplitEntity();
		split.setId("s1");
		split.setTitle("Dinner");
		split.setOn("2026-11-20");
		split.setTotal(new BigDecimal("300.00"));
		split.setTax(new BigDecimal("15.00"));
		split.setMode("equal");
		when(dao.findSplits(any())).thenReturn(List.of(split));
		when(dao.findShares(any())).thenReturn(List.of(share("h1", "s1", "h", "95.00", "5.00", "100.00"), share("m1", "s1", null, "95.00", "5.00", "100.00"), share("b1", "s1", "b", "95.00", "5.00", "100.00")));
		WealthPersonRollupEntity rollup = new WealthPersonRollupEntity();
		rollup.setId("r1");
		rollup.setPersonId("a");
		rollup.setOn("2026-02-02");
		rollup.setTotalOwed(new BigDecimal("777.00"));
		rollup.setAlreadyBack(BigDecimal.ZERO);
		rollup.setNewAmount(new BigDecimal("777.00"));
		rollup.setClosedItems(1);
		when(dao.findRollups(any())).thenReturn(List.of(rollup));
		WealthLendRequestEntity mine = new WealthLendRequestEntity();
		mine.setId("q1");
		mine.setPersonId("a");
		mine.setName("Arun S");
		mine.setAmount(new BigDecimal("100.00"));
		WealthLendRequestEntity hidden = new WealthLendRequestEntity();
		hidden.setId("q2");
		hidden.setPersonId("h");
		when(dao.findPendingRequests(any())).thenReturn(List.of(mine, hidden));
		WealthPersonAliasEntity alias = new WealthPersonAliasEntity();
		alias.setPersonId("a");
		alias.setAlias("Arun Kumar");
		when(dao.findAliases(any())).thenReturn(List.of(alias));
		WealthLendCommentEntity onEntry = new WealthLendCommentEntity();
		onEntry.setId("c1");
		onEntry.setEntryId("e2");
		onEntry.setAuthor("person");
		onEntry.setBody("Paid by UPI");
		WealthLendCommentEntity onShare = new WealthLendCommentEntity();
		onShare.setId("c2");
		onShare.setShareId("b1");
		onShare.setAuthor("owner");
		onShare.setBody("Please pay");
		when(dao.findComments(any())).thenReturn(List.of(onEntry, onShare));
		when(dao.findUnlinkedTransfers(any())).thenReturn(List.of(
				transfer("u2", "UPI transfer to Zed", "Transfer sent", "400.00", "2026-09-01"), transfer("u3", "Money from Zed", "Transfer received", "100.00", "2026-09-05"),
				transfer("u1", "UPI transfer to Arun Kumar", "Transfer sent", "50.00", "2026-10-01"), transfer("u4", "Liquiloans payout", "Transfer sent", "1000.00", "2026-10-02"),
				transfer("u5", "UPI transfer to Solo", "Transfer sent", "10.00", "2026-10-03")));

		PeopleOverviewDto dto = service.list();

		assertEquals(TODAY, dto.getAsOf());
		assertEquals(Timestamp.valueOf("2026-11-27 10:00:00"), dto.getFetchedAt());
		PersonDto arun = find(dto, "a");
		assertEquals(new BigDecimal("8924.02"), arun.getOwed(), "written-off and closed items are not counted");
		assertEquals(new BigDecimal("730.05"), arun.getInterest());
		assertEquals(new BigDecimal("10000.00"), arun.getLent());
		assertEquals(new BigDecimal("500.00"), arun.getWrittenOff());
		assertTrue(arun.isOverdue());
		assertFalse(arun.isHistory());
		assertEquals(3, arun.getItems().size(), "the closed item is history, not an open item");
		assertEquals("e4", arun.getItems().get(0).getEntryId(), "newest first");
		assertEquals("transfer", arun.getItems().get(1).getType());
		assertEquals("Paid back", arun.getItems().get(1).getLabel());
		assertEquals("UPI transfer to Arun S", arun.getItems().get(1).getDetail());
		assertEquals("Paid by UPI", arun.getItems().get(1).getComments().get(0).getBody());
		assertEquals("manual", arun.getItems().get(2).getType());
		assertEquals("Lent", arun.getItems().get(2).getLabel());
		assertEquals(1, arun.getRollups().size());
		assertEquals("e5", arun.getRollups().get(0).getItems().get(0).getEntryId());
		assertEquals(List.of("Arun Kumar"), arun.getAliases());
		assertEquals(1, arun.getUnlinkedCount(), "a transfer under his other name is his");
		assertEquals("u1", arun.getUnlinked().get(0).getId());
		assertEquals(1, arun.getRequests());

		PersonDto bina = find(dto, "b");
		assertEquals(new BigDecimal("100.00"), bina.getOwed());
		assertEquals(1, bina.getBills());
		assertEquals(1, bina.getUnpaidShares());
		assertEquals("Share of Dinner", bina.getItems().get(0).getLabel());
		assertEquals("₹95 + ₹5 GST", bina.getItems().get(0).getDetail());
		assertEquals("Please pay", bina.getItems().get(0).getComments().get(0).getBody());

		assertEquals(new BigDecimal("9024.02"), dto.getTotals().getOwed(), "hidden people are not in the totals");
		assertEquals(new BigDecimal("10100.00"), dto.getTotals().getLent());
		assertEquals(new BigDecimal("2000.00"), dto.getTotals().getRepaid());
		assertEquals(1, dto.getTotals().getHidden());
		assertEquals(2, dto.getTotals().getPeople());
		assertEquals(1, dto.getTotals().getOverdue());
		assertEquals(1, dto.getTotals().getSplits());
		assertEquals(1, dto.getRequests().size(), "a hidden person's request is not shown");
		assertEquals("q1", dto.getRequests().get(0).getId());

		assertEquals(3, dto.getSplits().get(0).getShares().size());
		assertEquals("You", dto.getSplits().get(0).getShares().get(0).getName(), "the owner's own part comes first");
		assertEquals(new BigDecimal("100.00"), dto.getSplits().get(0).getMyShare());
		assertEquals(new BigDecimal("200.00"), dto.getSplits().get(0).getOthersOwe());

		assertEquals(2, dto.getSuggestions().size());
		assertEquals("Liquiloans payout", dto.getSuggestions().get(0).getPerson());
		assertTrue(dto.getSuggestions().get(0).isP2p());
		assertEquals("Zed", dto.getSuggestions().get(1).getPerson());
		assertEquals(new BigDecimal("400.00"), dto.getSuggestions().get(1).getSent());
		assertEquals(new BigDecimal("100.00"), dto.getSuggestions().get(1).getReceived());
		assertEquals("2026-09-01", dto.getSuggestions().get(1).getFirstDay());
		assertEquals("2026-09-05", dto.getSuggestions().get(1).getLastDay());
	}

	@Test
	void aPaidShareIsMarkedPaidAndAPersonWhoOwesNothingIsHistory() {
		when(dao.findPeople(any())).thenReturn(List.of(person("b", "Bina", null, null, false)));
		WealthPersonEntryEntity paid = entry("p1", "b", "in", "100.00", "2026-11-25", "active");
		paid.setSplitId("s1");
		when(dao.findEntries(any())).thenReturn(List.of(paid));
		WealthBillSplitEntity split = new WealthBillSplitEntity();
		split.setId("s1");
		split.setTitle("Dinner");
		split.setOn("2026-11-20");
		split.setTotal(new BigDecimal("200.00"));
		split.setTax(BigDecimal.ZERO);
		when(dao.findSplits(any())).thenReturn(List.of(split));
		when(dao.findShares(any())).thenReturn(List.of(share("b1", "s1", "b", "100.00", "0.00", "100.00")));

		PeopleOverviewDto dto = service.list();

		PersonDto bina = find(dto, "b");
		assertEquals(0, bina.getOwed().signum());
		assertTrue(bina.isHistory());
		assertEquals(0, bina.getUnpaidShares());
		assertEquals("Paid share of Dinner", bina.getItems().get(0).getLabel());
		assertTrue(dto.getSplits().get(0).getShares().get(0).isPaid());
		assertNull(bina.getItems().get(1).getDetail(), "no GST text when there is no tax");
		assertTrue(bina.getItems().get(1).isPaid());
		assertEquals("2026-11-25", bina.getItems().get(1).getPaidOn());
	}

	@Test
	void anEmptyListIsAnEmptyOverviewAndRefreshReadsAgain() {
		PeopleOverviewDto dto = service.list();
		assertTrue(dto.getPeople().isEmpty());
		assertEquals(0, dto.getTotals().getOwed().signum());
		assertFalse(dto.isStale());
		assertEquals(TODAY, service.refresh().getAsOf());
	}
}
