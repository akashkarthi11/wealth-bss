package com.wealth.bss.serviceimplement;

import java.util.function.Supplier;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wealth.bss.common.model.dao.IfWealthUserConnectionDao;
import com.wealth.bss.common.model.entity.WealthUserSyncEntity;
import com.wealth.bss.constants.WealthServiceConstants;
import com.wealth.bss.nexus.service.IfNexusEventService;
import com.wealth.bss.serviceinterface.IfGmailService;
import com.wealth.bss.serviceinterface.IfGmailSyncService;
import com.wealth.common.generic.BaseError;
import com.wealth.common.generic.KarthiException;
import com.wealth.common.logger.KarthiLogger;
import com.wealth.common.model.dto.admin.GmailFetchResultDto;
import com.wealth.common.model.dto.admin.GmailStatementResultDto;
import com.wealth.common.model.dto.admin.GmailSyncRequestDto;
import com.wealth.common.model.dto.admin.GmailSyncStatusDto;
import com.wealth.common.utils.generic.RequestHeaderUtil;

@Service
public class GmailSyncServiceImple implements IfGmailSyncService {

	private static final KarthiLogger LOGGER = new KarthiLogger(GmailSyncServiceImple.class);
	private static final ObjectMapper MAPPER = new ObjectMapper();
	private static final String KEY_USER = "userId";
	private static final String KEY_KIND = "kind";
	private static final String KEY_ALERTS = "alerts";
	private static final String KEY_STATEMENTS = "statements";
	private static final String NOTE_STOPPED = "The read stopped before it finished. Read again.";
	private static final String NOTE_BROKEN = "The read could not finish. Read again.";

	private final IfWealthUserConnectionDao userDao;
	private final IfGmailService gmail;
	private final IfNexusEventService events;
	private final Executor workers;
	private final ScheduledExecutorService beat;
	private final long[] retryPausesMillis;

	@Autowired
	public GmailSyncServiceImple(IfWealthUserConnectionDao userDao, IfGmailService gmail, IfNexusEventService events) {
		this(userDao, gmail, events, Executors.newFixedThreadPool(WealthServiceConstants.SYNC_WORKERS, daemon("gmail-sync")), Executors.newSingleThreadScheduledExecutor(daemon("gmail-sync-beat")), java.util.Arrays.stream(WealthServiceConstants.SYNC_RETRY_PAUSE_SECONDS).mapToLong(seconds -> seconds * 1000L).toArray());
	}

	GmailSyncServiceImple(IfWealthUserConnectionDao userDao, IfGmailService gmail, IfNexusEventService events, Executor workers, ScheduledExecutorService beat, long[] retryPausesMillis) {
		this.userDao = userDao;
		this.gmail = gmail;
		this.events = events;
		this.workers = workers;
		this.beat = beat;
		this.retryPausesMillis = retryPausesMillis;
		events.onGmailSync(this::onRequest);
	}

	private static ThreadFactory daemon(String name) {
		return runnable -> {
			Thread thread = new Thread(runnable, name);
			thread.setDaemon(true);
			return thread;
		};
	}

	@Override
	public GmailSyncStatusDto start(GmailSyncRequestDto request) {
		String methodName = "start";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		String kind = request == null ? null : request.getKind();
		if (!WealthServiceConstants.SYNC_KIND_ALERTS.equals(kind) && !WealthServiceConstants.SYNC_KIND_STATEMENTS.equals(kind) && !WealthServiceConstants.SYNC_KIND_ALL.equals(kind)) {
			throw new KarthiException(BaseError.GMAIL_REQUEST_INVALID);
		}
		String userId = RequestHeaderUtil.getUserId();
		if (userDao.find(userId, WealthServiceConstants.GMAIL_USER) == null) {
			throw new KarthiException(BaseError.GMAIL_NOT_CONNECTED);
		}
		if (userDao.claimSync(userId, WealthServiceConstants.GMAIL_USER, kind, WealthServiceConstants.SYNC_STALE_SECONDS)) {
			ObjectNode payload = MAPPER.createObjectNode().put(KEY_USER, userId).put(KEY_KIND, kind);
			if (!events.publishGmailSync(userId, payload.toString())) {
				workers.execute(() -> run(userId, kind));
			}
		}
		LOGGER.info(WealthServiceConstants.EXITING + methodName);
		return status();
	}

	@Override
	public GmailSyncStatusDto status() {
		String userId = RequestHeaderUtil.getUserId();
		WealthUserSyncEntity row = userDao.findSync(userId, WealthServiceConstants.GMAIL_USER, WealthServiceConstants.SYNC_STALE_SECONDS);
		GmailSyncStatusDto dto = new GmailSyncStatusDto();
		if (row == null || row.getSyncStage() == null) {
			dto.setStage(WealthServiceConstants.SYNC_IDLE);
			return dto;
		}
		boolean active = isActive(row.getSyncStage());
		boolean stopped = active && row.isStale();
		dto.setKind(row.getSyncKind());
		dto.setStage(stopped ? WealthServiceConstants.SYNC_FAILED : row.getSyncStage());
		dto.setInProgress(active && !stopped);
		dto.setNote(stopped ? NOTE_STOPPED : row.getSyncNote());
		dto.setStartedAt(row.getSyncStartedAt() == null ? null : row.getSyncStartedAt().toInstant().toString());
		dto.setUpdatedAt(row.getSyncUpdatedAt() == null ? null : row.getSyncUpdatedAt().toInstant().toString());
		readResult(row.getSyncResult(), dto);
		return dto;
	}

	private static boolean isActive(String stage) {
		return WealthServiceConstants.SYNC_QUEUED.equals(stage) || WealthServiceConstants.SYNC_READING_ALERTS.equals(stage) || WealthServiceConstants.SYNC_READING_STATEMENTS.equals(stage);
	}

	// A request that came back from Kafka: the consumer thread only hands it to a worker, so a long read never holds up its polling.
	private void onRequest(String payload) {
		try {
			JsonNode node = MAPPER.readTree(payload);
			String userId = node.path(KEY_USER).asText("");
			String kind = node.path(KEY_KIND).asText("");
			if (!userId.isEmpty() && !kind.isEmpty()) {
				workers.execute(() -> run(userId, kind));
			}
		} catch (Exception e) {
			LOGGER.warn("A gmail.sync request could not be read (" + e.getClass().getSimpleName() + ")");
		}
	}

	// The read itself, on a worker, as the user who asked. Each part is kept apart: a failed alerts read does not stop the statements read; the note lists what failed as part=errorCode.
	void run(String userId, String kind) {
		String provider = WealthServiceConstants.GMAIL_USER;
		RequestHeaderUtil.setUserId(userId);
		ScheduledFuture<?> heartbeat = beat.scheduleAtFixedRate(() -> userDao.touchSync(userId, provider), WealthServiceConstants.SYNC_HEARTBEAT_SECONDS,
				WealthServiceConstants.SYNC_HEARTBEAT_SECONDS, TimeUnit.SECONDS);
		ObjectNode result = lastResult(userId);
		StringBuilder note = new StringBuilder();
		try {
			if (!WealthServiceConstants.SYNC_KIND_STATEMENTS.equals(kind)) {
				userDao.setSyncStage(userId, provider, WealthServiceConstants.SYNC_READING_ALERTS);
				result.remove(KEY_ALERTS);
				try {
					GmailFetchResultDto alerts = withRetries(gmail::fetch, KEY_ALERTS);
					result.set(KEY_ALERTS, MAPPER.valueToTree(alerts));
				} catch (KarthiException e) {
					note.append(KEY_ALERTS).append("=").append(e.getMessage()).append(" ");
				}
			}
			if (!WealthServiceConstants.SYNC_KIND_ALERTS.equals(kind)) {
				userDao.setSyncStage(userId, provider, WealthServiceConstants.SYNC_READING_STATEMENTS);
				result.remove(KEY_STATEMENTS);
				try {
					GmailStatementResultDto statements = withRetries(gmail::statements, KEY_STATEMENTS);
					result.set(KEY_STATEMENTS, MAPPER.valueToTree(statements));
				} catch (KarthiException e) {
					note.append(KEY_STATEMENTS).append("=").append(e.getMessage()).append(" ");
				}
			}
			boolean clean = note.length() == 0;
			userDao.finishSync(userId, provider, clean ? WealthServiceConstants.SYNC_DONE : WealthServiceConstants.SYNC_FAILED, clean ? null : note.toString().trim(), result.toString());
		} catch (RuntimeException e) {
			LOGGER.warn("A Gmail read stopped (" + e.getClass().getSimpleName() + ")");
			userDao.finishSync(userId, provider, WealthServiceConstants.SYNC_FAILED, NOTE_BROKEN, result.toString());
		} finally {
			heartbeat.cancel(false);
			RequestHeaderUtil.clear();
		}
	}

	// A read that failed only because Gmail could not be reached for a moment is tried again in the background after each pause; the sync keeps its heartbeat meanwhile, so the screen still shows it as reading.
	// Any other failure (not connected, nothing saved to read with) is final at once.
	private <T> T withRetries(Supplier<T> read, String part) {
		for (int attempt = 0;; attempt++) {
			try {
				return read.get();
			} catch (KarthiException e) {
				if (!BaseError.GMAIL_FETCH_FAILED.equals(e.getMessage()) || attempt >= retryPausesMillis.length) {
					throw e;
				}
				LOGGER.warn("The Gmail " + part + " read failed; trying again in " + retryPausesMillis[attempt] / 1000 + " s (retry " + (attempt + 1) + " of " + retryPausesMillis.length + ")");
				try {
					Thread.sleep(retryPausesMillis[attempt]);
				} catch (InterruptedException interrupted) {
					Thread.currentThread().interrupt();
					throw e;
				}
			}
		}
	}

	// What the last read found, so a read of one kind keeps the result of the other.
	private ObjectNode lastResult(String userId) {
		try {
			WealthUserSyncEntity before = userDao.findSync(userId, WealthServiceConstants.GMAIL_USER, WealthServiceConstants.SYNC_STALE_SECONDS);
			if (before != null && before.getSyncResult() != null && MAPPER.readTree(before.getSyncResult()) instanceof ObjectNode saved) {
				return saved;
			}
		} catch (Exception e) {
			LOGGER.warn("The last Gmail read result could not be read (" + e.getClass().getSimpleName() + ")");
		}
		return MAPPER.createObjectNode();
	}

	private static void readResult(String json, GmailSyncStatusDto dto) {
		if (json == null || json.isBlank()) {
			return;
		}
		try {
			JsonNode node = MAPPER.readTree(json);
			if (node.hasNonNull(KEY_ALERTS)) {
				dto.setAlerts(MAPPER.treeToValue(node.get(KEY_ALERTS), GmailFetchResultDto.class));
			}
			if (node.hasNonNull(KEY_STATEMENTS)) {
				dto.setStatements(MAPPER.treeToValue(node.get(KEY_STATEMENTS), GmailStatementResultDto.class));
			}
		} catch (Exception e) {
			LOGGER.warn("The saved Gmail read result could not be read (" + e.getClass().getSimpleName() + ")");
		}
	}
}
