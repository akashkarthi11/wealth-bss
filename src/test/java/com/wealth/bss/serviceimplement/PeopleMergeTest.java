package com.wealth.bss.serviceimplement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wealth.bss.common.model.dao.IfWealthPeopleDao;
import com.wealth.bss.common.model.entity.WealthPersonEntity;
import com.wealth.bss.nexus.service.IfNexusReadService;
import com.wealth.common.generic.KarthiException;
import com.wealth.common.model.dto.people.PersonMergeRequestDto;
import com.wealth.common.model.dto.people.PersonMergeResultDto;
import com.wealth.common.utils.generic.RequestHeaderUtil;

// Merging the same person entered twice: the old app's rules (mergePeople in api/_loans.js) with the DAO mocked.
class PeopleMergeTest {

	private static final String USER = "2a7014a8-9cf0-4d39-bed2-c83a83eddf0c";
	private static final String FROM = "11111111-1111-4111-8111-111111111111";
	private static final String INTO = "22222222-2222-4222-8222-222222222222";

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

	private static PersonMergeRequestDto merge(String from, String into) {
		PersonMergeRequestDto request = new PersonMergeRequestDto();
		request.setFromId(from);
		request.setIntoId(into);
		return request;
	}

	private static WealthPersonEntity person(String id, String name, String status) {
		WealthPersonEntity person = new WealthPersonEntity();
		person.setId(id);
		person.setName(name);
		person.setStatus(status);
		return person;
	}

	private void both() {
		when(dao.findPerson(USER, FROM)).thenReturn(person(FROM, "Mr Arun S", "open"));
		when(dao.findPerson(USER, INTO)).thenReturn(person(INTO, "Arun Kumar", "open"));
	}

	@Test
	void twoPeopleAreMergedAndTheMergedAwayNameIsKeptAsAnotherName() {
		both();

		PersonMergeResultDto result = service.mergePeople(merge(FROM, INTO));

		assertTrue(result.isStored());
		assertEquals("Mr Arun S", result.getFrom());
		assertEquals("Arun Kumar", result.getInto());
		assertEquals(INTO, result.getPersonId());
		verify(dao).mergePeople(USER, FROM, INTO, "Arun S");
		verify(nexus).evict("people", USER);
		verify(nexus).evict("transactions", USER);
	}

	@Test
	void aDryRunWritesNothing() {
		both();
		PersonMergeRequestDto request = merge(FROM, INTO);
		request.setDryRun(true);

		PersonMergeResultDto result = service.mergePeople(request);

		assertTrue(result.isDryRun());
		assertFalse(result.isStored());
		assertEquals("Arun Kumar", result.getInto());
		verify(dao, never()).mergePeople(any(), any(), any(), any());
		verify(nexus, never()).evict(any(), any());
	}

	@Test
	void twoPeopleInTheSameActiveSplitBillAreNotMerged() {
		both();
		when(dao.findMergeClash(INTO, FROM)).thenReturn("Team dinner");

		assertEquals("6070", code(() -> service.mergePeople(merge(FROM, INTO))));

		verify(dao, never()).mergePeople(any(), any(), any(), any());
	}

	@Test
	void theSamePersonTwiceABadIdAMissingOrADeletedPersonIsRefused() {
		assertEquals("611", code(() -> service.mergePeople(null)));
		assertEquals("611", code(() -> service.mergePeople(merge(FROM, FROM))), "pick two different people");
		assertEquals("611", code(() -> service.mergePeople(merge("x", INTO))));
		assertEquals("611", code(() -> service.mergePeople(merge(FROM, null))));
		assertEquals("6054", code(() -> service.mergePeople(merge(FROM, INTO))), "neither is the caller's");
		when(dao.findPerson(USER, FROM)).thenReturn(person(FROM, "Gone", "removed"));
		when(dao.findPerson(USER, INTO)).thenReturn(person(INTO, "Arun Kumar", "open"));
		assertEquals("6054", code(() -> service.mergePeople(merge(FROM, INTO))), "a deleted person");
		verify(dao, never()).mergePeople(any(), any(), any(), any());
	}
}
