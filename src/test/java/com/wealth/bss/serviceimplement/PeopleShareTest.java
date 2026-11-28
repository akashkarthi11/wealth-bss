package com.wealth.bss.serviceimplement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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
import com.wealth.bss.common.model.entity.WealthBillSplitEntity;
import com.wealth.bss.common.model.entity.WealthBillSplitShareEntity;
import com.wealth.bss.common.model.entity.WealthPersonEntity;
import com.wealth.bss.common.model.entity.WealthPersonEntryEntity;
import com.wealth.bss.common.utils.security.LoginRateLimiter;
import com.wealth.bss.nexus.service.IfNexusReadService;
import com.wealth.common.generic.KarthiException;
import com.wealth.common.model.dto.people.LendViewDto;
import com.wealth.common.model.dto.people.LendViewRequestDto;
import com.wealth.common.model.dto.people.ShareUpdateRequestDto;
import com.wealth.common.model.dto.people.ShareUpdateResultDto;
import com.wealth.common.utils.generic.RequestHeaderUtil;

// A person's share link (made, replaced, turned off) and the public page that reads it: the old app's rules (api/_lendshare.js) with the DAO mocked.
class PeopleShareTest {

	private static final String USER = "2a7014a8-9cf0-4d39-bed2-c83a83eddf0c";
	private static final String P1 = "11111111-1111-4111-8111-111111111111";
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
		RequestHeaderUtil.setUserId(USER);
		RequestHeaderUtil.setIp("203.0.113.5");
	}

	@AfterEach
	void tearDown() {
		RequestHeaderUtil.clear();
	}

	private static String code(Runnable call) {
		return assertThrows(KarthiException.class, call::run).getMessage();
	}

	private static ShareUpdateRequestDto share(boolean enabled) {
		ShareUpdateRequestDto request = new ShareUpdateRequestDto();
		request.setPersonId(P1);
		request.setEnabled(enabled);
		return request;
	}

	private void ownPerson() {
		WealthPersonEntity found = new WealthPersonEntity();
		found.setId(P1);
		found.setName("Dev");
		found.setStatus("open");
		when(dao.findPerson(USER, P1)).thenReturn(found);
	}

	private static LendViewRequestDto view(String token) {
		LendViewRequestDto request = new LendViewRequestDto();
		request.setToken(token);
		return request;
	}

	@Test
	void theHashIsTheOldAppsSha256InBase64Url() {
		assertEquals("ungWv48Bz-pBQUDeXa4iI7ADYaOWF3qctBD_YfIAFa0", PeopleServiceImple.hashOf("abc"));
	}

	@Test
	void aLinkIsMadeWithAFreshSecretAndOnlyItsHashIsKept() {
		ownPerson();
		when(dao.setShareToken(eq(USER), eq(P1), any())).thenReturn(true);

		ShareUpdateResultDto first = service.updateShare(share(true));
		ShareUpdateResultDto second = service.updateShare(share(true));

		assertTrue(first.isShared());
		assertTrue(first.getPath().matches("^/l/[A-Za-z0-9_-]{32}$"), first.getPath());
		assertFalse(first.getPath().equals(second.getPath()), "a new link each time");
		ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
		verify(dao, org.mockito.Mockito.times(2)).setShareToken(eq(USER), eq(P1), hash.capture());
		assertEquals(PeopleServiceImple.hashOf(first.getPath().substring(3)), hash.getAllValues().get(0));
		assertFalse(hash.getAllValues().get(0).contains(first.getPath().substring(3)), "the secret itself is not stored");
		verify(nexus, org.mockito.Mockito.times(2)).evict("people", USER);
	}

	@Test
	void aLinkIsTurnedOff() {
		ownPerson();
		when(dao.clearShareToken(USER, P1)).thenReturn(true);

		ShareUpdateResultDto result = service.updateShare(share(false));

		assertFalse(result.isShared());
		assertNull(result.getPath());
		verify(dao, never()).setShareToken(any(), any(), any());
		verify(nexus).evict("people", USER);
	}

	@Test
	void aLinkNeedsAnOwnPersonAndAValidRequest() {
		assertEquals("6054", code(() -> service.updateShare(share(true))), "not the caller's person");
		assertEquals("611", code(() -> service.updateShare(null)));
		ShareUpdateRequestDto noChoice = share(true);
		noChoice.setEnabled(null);
		assertEquals("611", code(() -> service.updateShare(noChoice)));
		ShareUpdateRequestDto badId = share(true);
		badId.setPersonId("x");
		assertEquals("611", code(() -> service.updateShare(badId)));
		verify(dao, never()).setShareToken(any(), any(), any());
	}

	@Test
	void theViewIsRefusedForABadOrUnknownSecretWithTheSameAnswer() {
		assertEquals("6068", code(() -> service.lendView(view(null))));
		assertEquals("6068", code(() -> service.lendView(view("short"))));
		assertEquals("6068", code(() -> service.lendView(view("has spaces and ! characters 1234567890"))));
		assertEquals("6068", code(() -> service.lendView(view(TOKEN))), "no person has this secret");
		assertEquals("6068", code(() -> service.lendView(null)));
		verify(dao, never()).findPeople(any());
	}

	@Test
	void everyCallCountsAgainstTheCallersAddressAndTooManyAreStopped() {
		when(limiter.attempt(eq("lendview:203.0.113.5"), anyLong(), anyLong())).thenReturn(false);

		assertEquals("6069", code(() -> service.lendView(view(TOKEN))));

		verify(dao, never()).findPersonByShareHash(any());
	}

	@Test
	void thePersonSeesOnlyTheirOwnLinesWithNoAccountTransferTextOrBillText() {
		WealthPersonEntity owner = new WealthPersonEntity();
		owner.setId(P1);
		owner.setUserId(USER);
		owner.setName("Dev");
		when(dao.findPersonByShareHash(PeopleServiceImple.hashOf(TOKEN))).thenReturn(owner);
		WealthPersonEntity person = new WealthPersonEntity();
		person.setId(P1);
		person.setName("Dev");
		person.setStatus("open");
		WealthPersonEntity someoneElse = new WealthPersonEntity();
		someoneElse.setId("99999999-9999-4999-8999-999999999999");
		someoneElse.setName("Secret Sam");
		someoneElse.setStatus("open");
		when(dao.findPeople(any())).thenReturn(List.of(person, someoneElse));
		WealthPersonEntryEntity lent = new WealthPersonEntryEntity();
		lent.setId("e1");
		lent.setPersonId(P1);
		lent.setDirection("out");
		lent.setAmount(new BigDecimal("1000.00"));
		lent.setOn("2026-10-01");
		lent.setStatus("active");
		lent.setTransactionId("t1");
		lent.setTxDescription("UPI transfer to Dev ref 998877");
		lent.setAccount("HDFC Savings ••4521");
		lent.setNote("for the shop");
		WealthPersonEntryEntity theirs = new WealthPersonEntryEntity();
		theirs.setId("e2");
		theirs.setPersonId("99999999-9999-4999-8999-999999999999");
		theirs.setDirection("out");
		theirs.setAmount(new BigDecimal("77777.00"));
		theirs.setOn("2026-10-02");
		theirs.setStatus("active");
		when(dao.findEntries(any())).thenReturn(List.of(lent, theirs));
		WealthBillSplitEntity split = new WealthBillSplitEntity();
		split.setId("s1");
		split.setTitle("Habib Co — UPI payment; Slice RuPay credit card ••6136");
		split.setOn("2026-11-20");
		split.setTotal(new BigDecimal("300.00"));
		split.setTax(BigDecimal.ZERO);
		split.setMode("equal");
		when(dao.findSplits(any())).thenReturn(List.of(split));
		WealthBillSplitShareEntity bill = new WealthBillSplitShareEntity();
		bill.setId("sh1");
		bill.setSplitId("s1");
		bill.setPersonId(P1);
		bill.setBase(new BigDecimal("100.00"));
		bill.setTax(BigDecimal.ZERO);
		bill.setAmount(new BigDecimal("100.00"));
		bill.setStatus("active");
		when(dao.findShares(any())).thenReturn(List.of(bill));

		LendViewDto dto = service.lendView(view(TOKEN));

		assertEquals("Dev", dto.getName());
		assertEquals(new BigDecimal("1100.00"), dto.getOwed());
		assertEquals(2, dto.getItems().size(), "only this person's lines");
		assertTrue(dto.getItems().stream().noneMatch(i -> i.getAmount().compareTo(new BigDecimal("77777.00")) == 0));
		assertTrue(dto.getItems().stream().anyMatch(i -> "Share of a bill".equals(i.getLabel()) && Boolean.FALSE.equals(i.getPaid())), "the bill's own text is not shown");
		assertTrue(dto.getItems().stream().anyMatch(i -> "for the shop".equals(i.getNote())));
		String everything = dto.getItems().toString() + dto.getName();
		assertFalse(everything.contains("6136") || everything.contains("HDFC") || everything.contains("998877") || everything.contains("Secret Sam"));
		verify(limiter).attempt(eq("lendview:203.0.113.5"), anyLong(), anyLong());
	}
}
