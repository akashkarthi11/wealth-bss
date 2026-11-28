package com.wealth.bss.serviceimplement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.wealth.bss.common.model.dao.IfWealthUserConnectionDao;
import com.wealth.bss.common.model.entity.WealthUserConnectionEntity;
import com.wealth.bss.common.model.entity.WealthUserSyncEntity;
import com.wealth.bss.constants.WealthServiceConstants;
import com.wealth.bss.nexus.service.IfNexusEventService;
import com.wealth.bss.serviceinterface.IfGmailService;
import com.wealth.common.generic.BaseError;
import com.wealth.common.generic.KarthiException;
import com.wealth.common.model.dto.admin.GmailFetchResultDto;
import com.wealth.common.model.dto.admin.GmailStatementResultDto;
import com.wealth.common.model.dto.admin.GmailSyncRequestDto;
import com.wealth.common.model.dto.admin.GmailSyncStatusDto;
import com.wealth.common.utils.generic.RequestHeaderUtil;

class GmailSyncServiceImpleTest {

	private static final String USER = "7f9c1a52-0000-4000-8000-000000000001";
	private static final String GMAIL = WealthServiceConstants.GMAIL_USER;
	private static final int STALE = WealthServiceConstants.SYNC_STALE_SECONDS;

	private IfWealthUserConnectionDao dao;
	private IfGmailService gmail;
	private IfNexusEventService events;
	private ScheduledFuture<?> heartbeat;
	private GmailSyncServiceImple service;
	private Consumer<String> kafkaHandler;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		dao = mock(IfWealthUserConnectionDao.class);
		gmail = mock(IfGmailService.class);
		events = mock(IfNexusEventService.class);
		ScheduledExecutorService beat = mock(ScheduledExecutorService.class);
		heartbeat = mock(ScheduledFuture.class);
		doReturn(heartbeat).when(beat).scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class));
		Executor direct = Runnable::run;
		service = new GmailSyncServiceImple(dao, gmail, events, direct, beat, new long[] { 0, 0 });
		ArgumentCaptor<Consumer<String>> captor = ArgumentCaptor.forClass(Consumer.class);
		verify(events).onGmailSync(captor.capture());
		kafkaHandler = captor.getValue();
		RequestHeaderUtil.setUserId(USER);
		when(dao.find(USER, GMAIL)).thenReturn(new WealthUserConnectionEntity());
	}

	@AfterEach
	void tearDown() {
		RequestHeaderUtil.clear();
	}

	private static GmailSyncRequestDto ask(String kind) {
		GmailSyncRequestDto request = new GmailSyncRequestDto();
		request.setKind(kind);
		return request;
	}

	private static WealthUserSyncEntity row(String stage, boolean stale) {
		WealthUserSyncEntity row = new WealthUserSyncEntity();
		row.setSyncKind("all");
		row.setSyncStage(stage);
		row.setStale(stale);
		row.setSyncStartedAt(new Timestamp(1_000));
		row.setSyncUpdatedAt(new Timestamp(2_000));
		return row;
	}

	@Test
	void aBadKindOrAnUnconnectedMailboxIsRefusedBeforeAnythingStarts() {
		assertEquals(BaseError.GMAIL_REQUEST_INVALID, assertThrows(KarthiException.class, () -> service.start(ask("everything"))).getMessage());
		assertEquals(BaseError.GMAIL_REQUEST_INVALID, assertThrows(KarthiException.class, () -> service.start(null)).getMessage());
		when(dao.find(USER, GMAIL)).thenReturn(null);
		assertEquals(BaseError.GMAIL_NOT_CONNECTED, assertThrows(KarthiException.class, () -> service.start(ask("all"))).getMessage());
		verify(dao, never()).claimSync(anyString(), anyString(), anyString(), anyInt());
	}

	@Test
	void aStartThatKafkaTakesIsLeftToTheConsumerAndTheStatusIsReturned() {
		when(dao.claimSync(USER, GMAIL, "all", STALE)).thenReturn(true);
		when(events.publishGmailSync(eq(USER), anyString())).thenReturn(true);
		when(dao.findSync(USER, GMAIL, STALE)).thenReturn(row("queued", false));

		GmailSyncStatusDto status = service.start(ask("all"));

		verify(events).publishGmailSync(eq(USER), contains("\"kind\":\"all\""));
		verify(gmail, never()).fetch();
		assertEquals("queued", status.getStage());
		assertTrue(status.isInProgress());
	}

	@Test
	void whenKafkaIsDownTheWorkerReadsAlertsThenStatementsAndSavesBothResults() {
		when(dao.claimSync(USER, GMAIL, "all", STALE)).thenReturn(true);
		when(events.publishGmailSync(eq(USER), anyString())).thenReturn(false);
		GmailFetchResultDto alerts = new GmailFetchResultDto();
		alerts.setRead(18);
		alerts.setAdded(12);
		when(gmail.fetch()).thenReturn(alerts);
		GmailStatementResultDto statements = new GmailStatementResultDto();
		statements.setRead(4);
		statements.setOpened(3);
		when(gmail.statements()).thenReturn(statements);

		service.start(ask("all"));

		verify(dao).setSyncStage(USER, GMAIL, "reading_alerts");
		verify(dao).setSyncStage(USER, GMAIL, "reading_statements");
		ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
		verify(dao).finishSync(eq(USER), eq(GMAIL), eq("done"), eq(null), json.capture());
		assertTrue(json.getValue().contains("\"read\":18") && json.getValue().contains("\"opened\":3"), json.getValue());
		verify(heartbeat).cancel(false);
		assertEquals(null, RequestHeaderUtil.getUserId(), "the worker clears the user it borrowed");
	}

	@Test
	void aFailedAlertsReadDoesNotStopTheStatementsAndTheNoteSaysWhatFailed() {
		when(dao.claimSync(USER, GMAIL, "all", STALE)).thenReturn(true);
		when(events.publishGmailSync(eq(USER), anyString())).thenReturn(false);
		when(gmail.fetch()).thenThrow(new KarthiException(BaseError.GMAIL_FETCH_FAILED));
		when(gmail.statements()).thenReturn(new GmailStatementResultDto());

		service.start(ask("all"));

		verify(gmail).statements();
		verify(dao).finishSync(eq(USER), eq(GMAIL), eq("failed"), eq("alerts=" + BaseError.GMAIL_FETCH_FAILED), anyString());
	}

	@Test
	void aStatementsReadThatFailsForAMomentIsTriedAgainInTheBackgroundUntilItWorks() {
		when(dao.claimSync(USER, GMAIL, "statements", STALE)).thenReturn(true);
		when(events.publishGmailSync(eq(USER), anyString())).thenReturn(false);
		GmailStatementResultDto statements = new GmailStatementResultDto();
		statements.setOpened(5);
		when(gmail.statements()).thenThrow(new KarthiException(BaseError.GMAIL_FETCH_FAILED)).thenThrow(new KarthiException(BaseError.GMAIL_FETCH_FAILED)).thenReturn(statements);

		service.start(ask("statements"));

		verify(gmail, times(3)).statements();
		verify(dao).finishSync(eq(USER), eq(GMAIL), eq("done"), eq(null), anyString());
	}

	@Test
	void aReadThatKeepsFailingGivesUpAfterTheRetriesAndSaysWhatFailed() {
		when(dao.claimSync(USER, GMAIL, "statements", STALE)).thenReturn(true);
		when(events.publishGmailSync(eq(USER), anyString())).thenReturn(false);
		when(gmail.statements()).thenThrow(new KarthiException(BaseError.GMAIL_FETCH_FAILED));

		service.start(ask("statements"));

		verify(gmail, times(3)).statements();
		verify(dao).finishSync(eq(USER), eq(GMAIL), eq("failed"), eq("statements=" + BaseError.GMAIL_FETCH_FAILED), anyString());
	}

	@Test
	void aFailureThatAnotherTryCannotFixIsNotRepeated() {
		when(dao.claimSync(USER, GMAIL, "statements", STALE)).thenReturn(true);
		when(events.publishGmailSync(eq(USER), anyString())).thenReturn(false);
		when(gmail.statements()).thenThrow(new KarthiException(BaseError.GMAIL_NOT_CONNECTED));

		service.start(ask("statements"));

		verify(gmail, times(1)).statements();
		verify(dao).finishSync(eq(USER), eq(GMAIL), eq("failed"), eq("statements=" + BaseError.GMAIL_NOT_CONNECTED), anyString());
	}

	@Test
	void aKindOfStatementsOnlyNeverTouchesTheAlerts() {
		when(dao.claimSync(USER, GMAIL, "statements", STALE)).thenReturn(true);
		when(events.publishGmailSync(eq(USER), anyString())).thenReturn(false);
		when(gmail.statements()).thenReturn(new GmailStatementResultDto());

		service.start(ask("statements"));

		verify(gmail, never()).fetch();
		verify(dao, never()).setSyncStage(USER, GMAIL, "reading_alerts");
	}

	@Test
	void aReadThatIsAlreadyInProgressStartsNothingNew() {
		when(dao.claimSync(USER, GMAIL, "alerts", STALE)).thenReturn(false);
		when(dao.findSync(USER, GMAIL, STALE)).thenReturn(row("reading_statements", false));

		GmailSyncStatusDto status = service.start(ask("alerts"));

		verify(events, never()).publishGmailSync(anyString(), anyString());
		verify(gmail, never()).fetch();
		assertEquals("reading_statements", status.getStage());
		assertTrue(status.isInProgress());
	}

	@Test
	void aRequestThatComesBackFromKafkaRunsOnAWorkerAsTheUserWhoAsked() {
		when(gmail.fetch()).thenReturn(new GmailFetchResultDto());

		kafkaHandler.accept("{\"userId\":\"" + USER + "\",\"kind\":\"alerts\"}");

		verify(gmail).fetch();
		verify(dao).finishSync(eq(USER), eq(GMAIL), eq("done"), eq(null), anyString());
		kafkaHandler.accept("not json");
		kafkaHandler.accept("{\"userId\":\"\",\"kind\":\"all\"}");
		verify(gmail).fetch();
	}

	@Test
	void theStatusIsIdleBeforeTheFirstReadAndFailedWhenARunningReadStoppedMoving() {
		when(dao.findSync(USER, GMAIL, STALE)).thenReturn(null);
		assertEquals("idle", service.status().getStage());
		assertFalse(service.status().isInProgress());

		when(dao.findSync(USER, GMAIL, STALE)).thenReturn(row("reading_alerts", true));
		GmailSyncStatusDto stopped = service.status();
		assertEquals("failed", stopped.getStage());
		assertFalse(stopped.isInProgress());
		assertNotNull(stopped.getNote());

		WealthUserSyncEntity done = row("done", false);
		done.setSyncResult("{\"alerts\":{\"read\":5,\"added\":2},\"statements\":{\"read\":1,\"opened\":1}}");
		when(dao.findSync(USER, GMAIL, STALE)).thenReturn(done);
		GmailSyncStatusDto finished = service.status();
		assertEquals("done", finished.getStage());
		assertEquals(5, finished.getAlerts().getRead());
		assertEquals(1, finished.getStatements().getOpened());
	}
}
