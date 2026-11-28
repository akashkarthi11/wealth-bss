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
import com.wealth.bss.common.model.entity.WealthBillSplitShareEntity;
import com.wealth.bss.common.model.entity.WealthPersonEntryEntity;
import com.wealth.bss.nexus.service.IfNexusReadService;
import com.wealth.common.generic.KarthiException;
import com.wealth.common.model.dto.people.RecordStatusRequestDto;
import com.wealth.common.model.dto.people.RecordStatusResultDto;
import com.wealth.common.utils.generic.RequestHeaderUtil;

// Writing a line or a share off, undoing that, and deleting a line: the rules of the old app (setRecordStatus in api/_loans.js) with the DAO mocked.
class PeopleRecordsTest {

	private static final String USER = "2a7014a8-9cf0-4d39-bed2-c83a83eddf0c";
	private static final String ENTRY = "41111111-1111-4111-8111-111111111111";
	private static final String SPLIT = "31111111-1111-4111-8111-111111111111";
	private static final String PERSON = "11111111-1111-4111-8111-111111111111";
	private static final String SHARE = "51111111-1111-4111-8111-111111111111";

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

	private static RecordStatusRequestDto entryTo(String status) {
		RecordStatusRequestDto request = new RecordStatusRequestDto();
		request.setEntryId(ENTRY);
		request.setStatus(status);
		return request;
	}

	private static RecordStatusRequestDto shareTo(String status) {
		RecordStatusRequestDto request = new RecordStatusRequestDto();
		request.setSplitId(SPLIT);
		request.setPersonId(PERSON);
		request.setStatus(status);
		return request;
	}

	private static WealthPersonEntryEntity entry(String status, String rollupId) {
		WealthPersonEntryEntity entry = new WealthPersonEntryEntity();
		entry.setId(ENTRY);
		entry.setStatus(status);
		entry.setRollupId(rollupId);
		return entry;
	}

	private static WealthBillSplitShareEntity share(String status, String rollupId) {
		WealthBillSplitShareEntity share = new WealthBillSplitShareEntity();
		share.setId(SHARE);
		share.setStatus(status);
		share.setRollupId(rollupId);
		return share;
	}

	@Test
	void aLineIsDeletedAndBothCachesAreCleared() {
		when(dao.findEntryForStatus(USER, ENTRY)).thenReturn(entry("active", null));
		when(dao.setEntryStatus(USER, ENTRY, "removed")).thenReturn(true);

		RecordStatusResultDto result = service.updateRecord(entryTo("removed"));

		assertTrue(result.isStored());
		assertEquals("active", result.getFrom());
		assertEquals("removed", result.getTo());
		verify(nexus).evict("people", USER);
		verify(nexus).evict("transactions", USER);
	}

	@Test
	void aLineIsWrittenOffAndTheWriteOffIsUndone() {
		when(dao.findEntryForStatus(USER, ENTRY)).thenReturn(entry("active", null));
		when(dao.setEntryStatus(USER, ENTRY, "written_off")).thenReturn(true);
		assertTrue(service.updateRecord(entryTo("written_off")).isStored());

		when(dao.findEntryForStatus(USER, ENTRY)).thenReturn(entry("written_off", null));
		when(dao.setEntryStatus(USER, ENTRY, "active")).thenReturn(true);
		RecordStatusResultDto undone = service.updateRecord(entryTo("active"));

		assertTrue(undone.isStored());
		assertEquals("written_off", undone.getFrom());
		assertEquals("active", undone.getTo());
	}

	@Test
	void theSameStatusStoresNothingAndADryRunWritesNothing() {
		when(dao.findEntryForStatus(USER, ENTRY)).thenReturn(entry("written_off", null));
		assertFalse(service.updateRecord(entryTo("written_off")).isStored());

		RecordStatusRequestDto dry = entryTo("removed");
		dry.setDryRun(true);
		RecordStatusResultDto result = service.updateRecord(dry);

		assertTrue(result.isDryRun());
		assertFalse(result.isStored());
		assertEquals("written_off", result.getFrom());
		verify(dao, never()).setEntryStatus(any(), any(), any());
		verify(nexus, never()).evict(any(), any());
	}

	@Test
	void aLineThatIsMissingDeletedOrClosedByARoundOffIsRefused() {
		assertEquals("6063", code(() -> service.updateRecord(entryTo("removed"))), "not the caller's or not found");
		when(dao.findEntryForStatus(USER, ENTRY)).thenReturn(entry("removed", null));
		assertEquals("6063", code(() -> service.updateRecord(entryTo("active"))), "already deleted");
		when(dao.findEntryForStatus(USER, ENTRY)).thenReturn(entry("active", "rollup-1"));
		assertEquals("6064", code(() -> service.updateRecord(entryTo("removed"))), "closed by a round-off");
		verify(dao, never()).setEntryStatus(any(), any(), any());
	}

	@Test
	void aShareIsWrittenOffAndUndoneButNeverDeleted() {
		when(dao.findShareForStatus(USER, SPLIT, PERSON)).thenReturn(share("active", null));
		when(dao.setShareStatus(USER, SHARE, "written_off")).thenReturn(true);

		RecordStatusResultDto result = service.updateRecord(shareTo("written_off"));

		assertTrue(result.isStored());
		assertEquals("active", result.getFrom());
		verify(nexus).evict("people", USER);
		verify(nexus, never()).evict("transactions", USER);
		assertEquals("611", code(() -> service.updateRecord(shareTo("removed"))), "cancel the split instead");
	}

	@Test
	void aShareThatIsMissingOrClosedByARoundOffIsRefused() {
		assertEquals("6058", code(() -> service.updateRecord(shareTo("written_off"))));
		when(dao.findShareForStatus(USER, SPLIT, PERSON)).thenReturn(share("active", "rollup-1"));
		assertEquals("6059", code(() -> service.updateRecord(shareTo("written_off"))));
		verify(dao, never()).setShareStatus(any(), any(), any());
	}

	@Test
	void aBadRequestIsRefusedBeforeAnythingIsRead() {
		assertEquals("611", code(() -> service.updateRecord(null)));
		assertEquals("611", code(() -> service.updateRecord(entryTo(null))));
		assertEquals("611", code(() -> service.updateRecord(entryTo("bogus"))));
		RecordStatusRequestDto badId = entryTo("removed");
		badId.setEntryId("x");
		assertEquals("611", code(() -> service.updateRecord(badId)));
		RecordStatusRequestDto noPerson = shareTo("written_off");
		noPerson.setPersonId(null);
		assertEquals("611", code(() -> service.updateRecord(noPerson)));
		verify(dao, never()).findEntryForStatus(any(), any());
		verify(dao, never()).findShareForStatus(any(), any(), any());
	}
}
