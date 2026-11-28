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
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.wealth.bss.common.model.dao.IfWealthPeopleDao;
import com.wealth.bss.common.model.entity.WealthPersonEntity;
import com.wealth.bss.nexus.service.IfNexusReadService;
import com.wealth.common.generic.KarthiException;
import com.wealth.common.model.dto.people.PersonSaveRequestDto;
import com.wealth.common.model.dto.people.PersonSaveResultDto;
import com.wealth.common.utils.generic.RequestHeaderUtil;

// Adding and changing a person (due day, yearly rate, kind, note, hidden) and deleting one: the rules of the old app (savePerson in api/_loans.js) with the DAO mocked.
class PeoplePersonSaveTest {

	private static final String USER = "2a7014a8-9cf0-4d39-bed2-c83a83eddf0c";
	private static final String P1 = "11111111-1111-4111-8111-111111111111";

	private IfWealthPeopleDao dao;
	private IfNexusReadService nexus;
	private PeopleServiceImple service;

	@BeforeEach
	void setUp() {
		dao = mock(IfWealthPeopleDao.class);
		nexus = mock(IfNexusReadService.class);
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

	private static PersonSaveRequestDto edit() {
		PersonSaveRequestDto request = new PersonSaveRequestDto();
		request.setPersonId(P1);
		return request;
	}

	private void existing(String name, String status) {
		WealthPersonEntity found = new WealthPersonEntity();
		found.setId(P1);
		found.setName(name);
		found.setStatus(status);
		when(dao.findPerson(USER, P1)).thenReturn(found);
	}

	@Test
	void aNewPersonGetsTheDefaultsAndTheCacheIsCleared() {
		when(dao.insertPerson(eq(USER), any())).thenReturn(P1);
		PersonSaveRequestDto request = new PersonSaveRequestDto();
		request.setName("  Arun   S ");
		request.setDueOn("2026-12-20");
		request.setAnnualRate("12");
		request.setNote("for the shop");

		PersonSaveResultDto result = service.savePerson(request);

		assertTrue(result.isStored());
		assertEquals(P1, result.getPersonId());
		ArgumentCaptor<WealthPersonEntity> saved = ArgumentCaptor.forClass(WealthPersonEntity.class);
		verify(dao).insertPerson(eq(USER), saved.capture());
		assertEquals("Arun S", saved.getValue().getName(), "extra spaces are squeezed");
		assertEquals("personal", saved.getValue().getKind());
		assertEquals("open", saved.getValue().getStatus());
		assertEquals("2026-12-20", saved.getValue().getDueOn(), "a due day may be in the future");
		assertEquals(new BigDecimal("0.120000"), saved.getValue().getAnnualRate(), "12 % a year is stored as a fraction");
		assertFalse(saved.getValue().isHidden());
		verify(nexus).evict("people", USER);
	}

	@Test
	void aNewPersonNeedsAFreeName() {
		assertEquals("611", code(() -> service.savePerson(new PersonSaveRequestDto())), "a name is needed");
		PersonSaveRequestDto blank = new PersonSaveRequestDto();
		blank.setName("   ");
		assertEquals("611", code(() -> service.savePerson(blank)));
		PersonSaveRequestDto taken = new PersonSaveRequestDto();
		taken.setName("Arun S");
		assertEquals("6065", code(() -> service.savePerson(taken)), "already in your people");
		verify(nexus, never()).evict(any(), any());
	}

	@Test
	void theRateAndTheDueDayAreChangedAndClearedWithEmptyText() {
		existing("Dev", "open");
		when(dao.updatePerson(eq(USER), eq(P1), any(), any(), any())).thenReturn(true);
		PersonSaveRequestDto request = edit();
		request.setAnnualRate("18.5");
		request.setDueOn("2027-01-31");

		PersonSaveResultDto result = service.savePerson(request);

		assertTrue(result.isStored());
		assertEquals(java.util.List.of("dueOn", "annualRate"), result.getChanged());
		@SuppressWarnings("unchecked")
		ArgumentCaptor<Set<String>> changed = ArgumentCaptor.forClass(Set.class);
		ArgumentCaptor<WealthPersonEntity> values = ArgumentCaptor.forClass(WealthPersonEntity.class);
		verify(dao).updatePerson(eq(USER), eq(P1), changed.capture(), values.capture(), eq(null));
		assertEquals(Set.of("dueOn", "annualRate"), changed.getValue());
		assertEquals(new BigDecimal("0.185000"), values.getValue().getAnnualRate());
		assertEquals("2027-01-31", values.getValue().getDueOn());

		PersonSaveRequestDto clear = edit();
		clear.setAnnualRate("");
		clear.setDueOn("");
		clear.setNote("");
		service.savePerson(clear);
		verify(dao).updatePerson(eq(USER), eq(P1), eq(Set.of("dueOn", "annualRate", "note")), values.capture(), eq(null));
		assertNull(values.getValue().getAnnualRate());
		assertNull(values.getValue().getDueOn());
		assertNull(values.getValue().getNote());
	}

	@Test
	void aRenameKeepsTheOldNameAsAnotherNameAndTheSameNameChangesNothing() {
		existing("Mr Arun S", "open");
		when(dao.updatePerson(eq(USER), eq(P1), any(), any(), any())).thenReturn(true);
		PersonSaveRequestDto rename = edit();
		rename.setName("Arun Kumar");

		service.savePerson(rename);

		verify(dao).updatePerson(eq(USER), eq(P1), eq(Set.of("name")), any(), eq("Arun S"));

		PersonSaveRequestDto same = edit();
		same.setName("Mr Arun S");
		assertEquals("611", code(() -> service.savePerson(same)), "nothing to change");
	}

	@Test
	void aRateAKindAStatusOrADateThatDoesNotFitIsRefused() {
		for (String rate : new String[] { "abc", "-1", "100.5" }) {
			PersonSaveRequestDto request = edit();
			request.setAnnualRate(rate);
			assertEquals("6067", code(() -> service.savePerson(request)), rate);
		}
		PersonSaveRequestDto date = edit();
		date.setDueOn("next week");
		assertEquals("6062", code(() -> service.savePerson(date)));
		PersonSaveRequestDto kind = edit();
		kind.setKind("robot");
		assertEquals("611", code(() -> service.savePerson(kind)));
		PersonSaveRequestDto status = edit();
		status.setStatus("gone");
		assertEquals("611", code(() -> service.savePerson(status)));
		PersonSaveRequestDto badId = edit();
		badId.setPersonId("x");
		assertEquals("611", code(() -> service.savePerson(badId)));
		verify(dao, never()).updatePerson(any(), any(), any(), any(), any());
	}

	@Test
	void aPersonWhoIsMissingOrAlreadyDeletedIsRefused() {
		PersonSaveRequestDto request = edit();
		request.setNote("x");
		assertEquals("6054", code(() -> service.savePerson(request)), "not the caller's");
		existing("Gone", "removed");
		assertEquals("6054", code(() -> service.savePerson(request)), "already deleted");
	}

	@Test
	void aPersonIsDeletedOnlyWhenNoLinesOrSharesAreLeft() {
		existing("Dev", "open");
		when(dao.countPersonRecords(P1)).thenReturn(2);
		PersonSaveRequestDto delete = edit();
		delete.setStatus("removed");
		assertEquals("6066", code(() -> service.savePerson(delete)), "remove or write off the lines first");
		verify(dao, never()).updatePerson(any(), any(), any(), any(), any());

		when(dao.countPersonRecords(P1)).thenReturn(0);
		when(dao.updatePerson(eq(USER), eq(P1), any(), any(), any())).thenReturn(true);
		assertTrue(service.savePerson(delete).isStored());
		verify(dao).updatePerson(eq(USER), eq(P1), eq(Set.of("status")), any(), eq(null));
	}

	@Test
	void aDryRunStoresNothing() {
		existing("Dev", "open");
		PersonSaveRequestDto request = edit();
		request.setAnnualRate("12");
		request.setDryRun(true);

		PersonSaveResultDto result = service.savePerson(request);

		assertTrue(result.isDryRun());
		assertFalse(result.isStored());
		assertEquals(java.util.List.of("annualRate"), result.getChanged());
		verify(dao, never()).updatePerson(any(), any(), any(), any(), any());
		verify(nexus, never()).evict(any(), any());
	}
}
