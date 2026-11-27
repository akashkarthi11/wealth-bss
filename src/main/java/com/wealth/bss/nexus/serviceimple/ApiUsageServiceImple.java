package com.wealth.bss.nexus.serviceimple;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.BooleanSupplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.wealth.bss.common.model.dao.IfWealthApiUsageDao;
import com.wealth.bss.common.model.entity.WealthApiUsageEntity;
import com.wealth.bss.config.RedisEndpoint;
import com.wealth.bss.constants.WealthServiceConstants;
import com.wealth.bss.nexus.service.IfApiUsageService;
import com.wealth.bss.serviceinterface.IfRequestContextService;
import com.wealth.common.generic.BaseError;
import com.wealth.common.generic.KarthiException;
import com.wealth.common.interceptor.ApiUsageRecorder;
import com.wealth.common.logger.KarthiLogger;
import com.wealth.common.model.dto.admin.ApiUsageDto;
import com.wealth.common.model.dto.admin.ApiUsageRequestDto;
import com.wealth.common.model.dto.admin.ApiUsageRowDto;
import com.wealth.common.model.dto.admin.ApiUsageUserDto;
import com.wealth.common.model.dto.admin.UsageBatchDto;
import com.wealth.common.model.dto.admin.UsageCountDto;
import com.wealth.common.utils.generic.RequestHeaderUtil;

// API usage counting (the nexus owns Redis). Every finished request and every outgoing call adds to a count held in memory; every few seconds the counts
// are added to a Redis hash per day and kind (atomic adds); every few minutes the hash is moved aside, added to the database in one transaction with a
// batch ledger (so a retry never counts twice) and deleted. When Redis is Inactive or not answering, the counts stay in memory and are saved to the
// database directly (rules 10a). Counting never fails or slows a call.
@Service
public class ApiUsageServiceImple implements IfApiUsageService, ApiUsageRecorder {

	private static final KarthiLogger LOGGER = new KarthiLogger(ApiUsageServiceImple.class);
	private static final ZoneId IST = ZoneId.of(WealthServiceConstants.BROKER_ZONE);
	private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;
	private static final String CALLS = "calls";
	private static final String SUCCESS = "ok";
	private static final String CLIENT = "4xx";
	private static final String FAILED = "fail";
	private static final String MILLIS = "ms";
	private static final String FLUSH = "flush";

	private static final Pattern USER_ID = Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

	// user is the caller's id, or "-" when there was none.
	private record Key(LocalDate day, String kind, String name, String user, String field) {
	}

	// What one name adds up to.
	private static final class Sums {
		private long calls;
		private long success;
		private long clientErrors;
		private long failures;
		private long totalMs;
	}

	private static final List<String> KINDS = List.of(WealthServiceConstants.USAGE_BSS, WealthServiceConstants.USAGE_BFF, WealthServiceConstants.USAGE_EXTERNAL,
			WealthServiceConstants.USAGE_JOB);
	// The scheduled job running on this thread, so an outside call it makes is counted as that job's.
	private static final ThreadLocal<String> JOB = new ThreadLocal<>();

	private final StringRedisTemplate redis;
	private final RedisEndpoint endpoint;
	private final IfWealthApiUsageDao dao;
	private final IfRequestContextService requestContextService;
	private final Clock clock;
	private final String serviceKey;
	private final String instance = UUID.randomUUID().toString().substring(0, 8);
	private final ConcurrentHashMap<Key, LongAdder> buffer = new ConcurrentHashMap<>();
	private volatile long redisRetryAt;
	private volatile boolean degraded;
	private volatile boolean overflowLogged;
	private volatile Timestamp savedAt;

	@Autowired
	public ApiUsageServiceImple(StringRedisTemplate redis, RedisEndpoint endpoint, IfWealthApiUsageDao dao, IfRequestContextService requestContextService,
			@Value("${WEALTH_SERVICE_KEY:}") String serviceKey) {
		this(redis, endpoint, dao, requestContextService, serviceKey, Clock.systemUTC());
	}

	ApiUsageServiceImple(StringRedisTemplate redis, RedisEndpoint endpoint, IfWealthApiUsageDao dao, IfRequestContextService requestContextService,
			String serviceKey, Clock clock) {
		this.redis = redis;
		this.endpoint = endpoint;
		this.dao = dao;
		this.requestContextService = requestContextService;
		this.serviceKey = serviceKey == null ? "" : serviceKey;
		this.clock = clock;
	}

	// ---- counting: in memory, never throws ----

	// Our own API, once per finished request. The name is the route template Spring matched, so ids in the address never make new names.
	@Override
	public void record(String method, String route, int status, long millis) {
		try {
			if ("OPTIONS".equalsIgnoreCase(method) || (route != null && (route.startsWith("/actuator") || route.startsWith(WealthServiceConstants.API_USAGE_BASE)
					|| route.startsWith(WealthServiceConstants.INTERNAL_USAGE_BASE)))) {
				return;
			}
			String name = route == null ? WealthServiceConstants.USAGE_UNMATCHED : method + " " + route;
			int family = status / 100;
			add(WealthServiceConstants.USAGE_BSS, name, callerOrNone(), family == 4 ? CLIENT : family == 5 || status <= 0 ? FAILED : SUCCESS, millis);
		} catch (RuntimeException e) {
			LOGGER.warn("API usage not counted: " + e.getClass().getSimpleName());
		}
	}

	// A call to an outside API. Made inside a scheduled job, it is listed apart: the name ends with "(job <name>)".
	@Override
	public void recordExternal(String provider, String endpointName, boolean ok, long millis) {
		try {
			String job = JOB.get();
			add(WealthServiceConstants.USAGE_EXTERNAL, provider + " " + endpointName + (job == null ? "" : " (job " + job + ")"),
					job == null ? callerOrNone() : WealthServiceConstants.USAGE_NO_USER, ok ? SUCCESS : FAILED, millis);
		} catch (RuntimeException e) {
			LOGGER.warn("API usage not counted: " + e.getClass().getSimpleName());
		}
	}

	// Runs a scheduled job and counts the run: ok when the work says so, failed when it says not or throws (the exception is passed on). Any outside call
	// the job makes on this thread is counted with the job's name. A job that does not use this is not counted.
	@Override
	public void runJob(String name, BooleanSupplier work) {
		long started = System.nanoTime();
		boolean worked = false;
		JOB.set(name);
		try {
			worked = work.getAsBoolean();
		} finally {
			JOB.remove();
			try {
				add(WealthServiceConstants.USAGE_JOB, name, WealthServiceConstants.USAGE_NO_USER, worked ? SUCCESS : FAILED, (System.nanoTime() - started) / 1_000_000L);
			} catch (RuntimeException e) {
				LOGGER.warn("API usage not counted: " + e.getClass().getSimpleName());
			}
		}
	}

	// The BFF's counts, reported with the shared service key. Refused without the key; a report with a wrong shape is refused whole, so a bad sender cannot
	// fill the memory or the table.
	@Override
	public int recordFromBff(String presentedKey, UsageBatchDto batch) {
		if (serviceKey.isEmpty() || presentedKey == null
				|| !MessageDigest.isEqual(serviceKey.getBytes(StandardCharsets.UTF_8), presentedKey.getBytes(StandardCharsets.UTF_8))) {
			throw new KarthiException(BaseError.SERVICE_KEY_REJECTED);
		}
		List<UsageCountDto> counts = batch == null ? null : batch.getCounts();
		if (counts == null || counts.size() > WealthServiceConstants.USAGE_BATCH_MAX) {
			throw new KarthiException(BaseError.INVALID_REQUEST);
		}
		for (UsageCountDto one : counts) {
			if (one == null || one.getName() == null || one.getName().isBlank() || one.getName().length() > WealthServiceConstants.USAGE_NAME_MAX
					|| invalid(one.getCalls()) || invalid(one.getSuccess()) || invalid(one.getClientErrors()) || invalid(one.getFailures()) || invalid(one.getTotalMs())
					|| one.getCalls() < one.getSuccess() + one.getClientErrors() + one.getFailures()
					|| (one.getUserId() != null && !one.getUserId().isBlank() && !USER_ID.matcher(one.getUserId().trim()).matches())) {
				throw new KarthiException(BaseError.INVALID_REQUEST);
			}
		}
		LocalDate day = today();
		for (UsageCountDto one : counts) {
			String user = userOf(one.getUserId());
			count(new Key(day, WealthServiceConstants.USAGE_BFF, one.getName(), user, CALLS), one.getCalls());
			count(new Key(day, WealthServiceConstants.USAGE_BFF, one.getName(), user, SUCCESS), one.getSuccess());
			count(new Key(day, WealthServiceConstants.USAGE_BFF, one.getName(), user, CLIENT), one.getClientErrors());
			count(new Key(day, WealthServiceConstants.USAGE_BFF, one.getName(), user, FAILED), one.getFailures());
			count(new Key(day, WealthServiceConstants.USAGE_BFF, one.getName(), user, MILLIS), one.getTotalMs());
		}
		return counts.size();
	}

	// The user of this request, or "-" when none is known (a public call, a job, the system).
	private static String callerOrNone() {
		return userOf(RequestHeaderUtil.getUserId());
	}

	private static String userOf(String id) {
		return id != null && USER_ID.matcher(id.trim()).matches() ? id.trim().toLowerCase() : WealthServiceConstants.USAGE_NO_USER;
	}

	// "name|user|field", or the older "name|field" (no user) that may still be in Redis from before users were counted. Null when it is neither.
	private static String[] split(String text) {
		int last = text.lastIndexOf('|');
		if (last <= 0) {
			return null;
		}
		int before = text.lastIndexOf('|', last - 1);
		if (before > 0) {
			String user = text.substring(before + 1, last);
			if (WealthServiceConstants.USAGE_NO_USER.equals(user) || USER_ID.matcher(user).matches()) {
				return new String[] { text.substring(0, before), user, text.substring(last + 1) };
			}
		}
		return new String[] { text.substring(0, last), WealthServiceConstants.USAGE_NO_USER, text.substring(last + 1) };
	}

	private static boolean invalid(long value) {
		return value < 0 || value > WealthServiceConstants.USAGE_COUNT_MAX;
	}

	private void add(String kind, String name, String user, String result, long millis) {
		LocalDate day = today();
		count(new Key(day, kind, name, user, CALLS), 1);
		count(new Key(day, kind, name, user, result), 1);
		count(new Key(day, kind, name, user, MILLIS), Math.max(millis, 0));
	}

	private void count(Key key, long amount) {
		LongAdder adder = buffer.get(key);
		if (adder == null) {
			if (buffer.size() >= WealthServiceConstants.USAGE_BUFFER_MAX) {
				if (!overflowLogged) {
					overflowLogged = true;
					LOGGER.warn("API usage buffer is full; new names are not counted until it is saved");
				}
				return;
			}
			adder = buffer.computeIfAbsent(key, k -> new LongAdder());
		}
		adder.add(amount);
	}

	private LocalDate today() {
		return LocalDate.now(clock.withZone(IST));
	}

	// ---- memory to Redis, every few seconds ----

	@Scheduled(fixedDelay = WealthServiceConstants.USAGE_DRAIN_MILLIS)
	public void drain() {
		if (!useRedis()) {
			return;
		}
		Map<Key, Long> taken = take();
		if (taken.isEmpty()) {
			return;
		}
		try {
			Set<String> touched = new java.util.HashSet<>();
			for (Map.Entry<Key, Long> entry : taken.entrySet()) {
				Key key = entry.getKey();
				String hash = hashKey(key.day(), key.kind());
				redis.opsForHash().increment(hash, key.name() + "|" + key.user() + "|" + key.field(), entry.getValue());
				touched.add(hash);
			}
			for (String hash : touched) {
				redis.expire(hash, Duration.ofDays(WealthServiceConstants.USAGE_KEEP_DAYS));
			}
			degraded = false;
		} catch (RuntimeException e) {
			putBack(taken);
			redisFailed(e);
		}
	}

	// Redis is used while it is Active and has not just failed.
	private boolean useRedis() {
		return endpoint.isEnabled() && System.currentTimeMillis() >= redisRetryAt;
	}

	private void redisFailed(RuntimeException e) {
		redisRetryAt = System.currentTimeMillis() + WealthServiceConstants.REDIS_RETRY_MILLIS;
		if (!degraded) {
			degraded = true;
			LOGGER.warn("API usage: Redis is not answering (" + e.getClass().getSimpleName() + "); counts stay in memory and are saved to the database directly");
		}
	}

	// What was counted since the last take, each counter set back to zero in the same step.
	private Map<Key, Long> take() {
		Map<Key, Long> taken = new LinkedHashMap<>();
		for (Map.Entry<Key, LongAdder> entry : buffer.entrySet()) {
			long value = entry.getValue().sumThenReset();
			if (value != 0) {
				taken.put(entry.getKey(), value);
			}
		}
		return taken;
	}

	private void putBack(Map<Key, Long> taken) {
		for (Map.Entry<Key, Long> entry : taken.entrySet()) {
			buffer.computeIfAbsent(entry.getKey(), k -> new LongAdder()).add(entry.getValue());
		}
	}

	private String hashKey(LocalDate day, String kind) {
		return WealthServiceConstants.USAGE_KEY_PREFIX + DAY.format(day) + ":" + kind;
	}

	// ---- Redis to the database, every few minutes ----

	@Scheduled(cron = WealthServiceConstants.USAGE_SAVE_CRON, zone = WealthServiceConstants.BROKER_ZONE)
	public void save() {
		try {
			if (useRedis()) {
				drain();
				moveAside();
				savePending();
			}
			if (!useRedis()) {
				saveFromMemory();
			}
		} catch (RuntimeException e) {
			LOGGER.warn("API usage not saved this time: " + e.getClass().getSimpleName());
		}
	}

	// The day's hash is renamed in one step: counts that arrive after it start a new hash, so none is lost or counted twice.
	private void moveAside() {
		LocalDate today = today();
		for (LocalDate day : List.of(today, today.minusDays(1))) {
			for (String kind : KINDS) {
				try {
					String key = hashKey(day, kind);
					if (Boolean.TRUE.equals(redis.hasKey(key))) {
						String moved = WealthServiceConstants.USAGE_KEY_PREFIX + FLUSH + ":" + DAY.format(day) + ":" + kind + ":" + clock.millis();
						redis.rename(key, moved);
						redis.opsForSet().add(WealthServiceConstants.USAGE_PENDING_KEY, moved);
					}
				} catch (RuntimeException e) {
					redisFailed(e);
				}
			}
		}
	}

	private void savePending() {
		Set<String> pending = redis.opsForSet().members(WealthServiceConstants.USAGE_PENDING_KEY);
		if (pending == null) {
			return;
		}
		for (String moved : pending) {
			try {
				String[] parts = moved.split(":");
				if (parts.length < 6) {
					redis.opsForSet().remove(WealthServiceConstants.USAGE_PENDING_KEY, moved);
					continue;
				}
				LocalDate day = LocalDate.parse(parts[3], DAY);
				Map<Object, Object> entries = redis.opsForHash().entries(moved);
				List<WealthApiUsageEntity> rows = rows(day, parts[4], entries);
				if (rows.isEmpty() || saved(moved, rows)) {
					redis.delete(moved);
					redis.opsForSet().remove(WealthServiceConstants.USAGE_PENDING_KEY, moved);
				}
			} catch (RuntimeException e) {
				LOGGER.warn("API usage batch kept for the next save: " + e.getClass().getSimpleName());
			}
		}
	}

	// The batch is added once: a batch id the database already has is not added again, and counts as saved.
	private boolean saved(String batchId, List<WealthApiUsageEntity> rows) {
		dao.saveBatch(batchId, rows);
		savedAt = new Timestamp(clock.millis());
		return true;
	}

	// With Redis not available the counts held in memory go to the database directly.
	private void saveFromMemory() {
		Map<Key, Long> taken = take();
		if (taken.isEmpty()) {
			return;
		}
		Map<String, Map<Object, Object>> groups = new LinkedHashMap<>();
		for (Map.Entry<Key, Long> entry : taken.entrySet()) {
			Key key = entry.getKey();
			groups.computeIfAbsent(DAY.format(key.day()) + ":" + key.kind(), g -> new LinkedHashMap<>()).put(key.name() + "|" + key.user() + "|" + key.field(), entry.getValue());
		}
		for (Map.Entry<String, Map<Object, Object>> group : groups.entrySet()) {
			String[] parts = group.getKey().split(":");
			try {
				saved("mem:" + instance + ":" + clock.millis() + ":" + group.getKey(), rows(LocalDate.parse(parts[0], DAY), parts[1], group.getValue()));
			} catch (RuntimeException e) {
				for (Map.Entry<Object, Object> field : group.getValue().entrySet()) {
					String[] one = split(field.getKey().toString());
					count(new Key(LocalDate.parse(parts[0], DAY), parts[1], one[0], one[1], one[2]), Long.parseLong(field.getValue().toString()));
				}
				LOGGER.warn("API usage kept in memory for the next save: " + e.getClass().getSimpleName());
			}
		}
	}

	// "name|user|field" entries to one row per name and user.
	private List<WealthApiUsageEntity> rows(LocalDate day, String kind, Map<Object, Object> entries) {
		Map<String, WealthApiUsageEntity> byName = new LinkedHashMap<>();
		for (Map.Entry<Object, Object> entry : entries.entrySet()) {
			String[] one = split(entry.getKey().toString());
			if (one == null) {
				continue;
			}
			WealthApiUsageEntity row = byName.computeIfAbsent(one[1] + "|" + one[0], k -> {
				WealthApiUsageEntity created = new WealthApiUsageEntity();
				created.setDay(day);
				created.setKind(kind);
				created.setName(one[0]);
				created.setUserId(WealthServiceConstants.USAGE_NO_USER.equals(one[1]) ? WealthServiceConstants.USAGE_NO_USER_ID : one[1]);
				return created;
			});
			long value = Long.parseLong(entry.getValue().toString());
			switch (one[2]) {
			case CALLS -> row.setCalls(value);
			case SUCCESS -> row.setSuccess(value);
			case CLIENT -> row.setClientErrors(value);
			case FAILED -> row.setFailures(value);
			case MILLIS -> row.setTotalMs(value);
			default -> {
			}
			}
		}
		return new ArrayList<>(byName.values());
	}

	// ---- the admin screen ----

	@Override
	public ApiUsageDto list(ApiUsageRequestDto request) {
		String methodName = "list";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		requestContextService.requireAdmin();
		int days = request == null || request.getDays() == null ? WealthServiceConstants.USAGE_DAYS_DEFAULT : request.getDays();
		if (days < 1 || days > WealthServiceConstants.USAGE_DAYS_MAX) {
			throw new KarthiException(BaseError.INVALID_REQUEST);
		}
		String only = userFilter(request);
		LocalDate today = today();
		LocalDate from = today.minusDays(days - 1L);
		Map<String, Map<String, Sums>> totals = new LinkedHashMap<>();
		for (String kind : KINDS) {
			totals.put(kind, new LinkedHashMap<>());
		}
		Map<String, Long> userCalls = new LinkedHashMap<>();
		for (WealthApiUsageEntity row : dao.findUserCallsSince(from)) {
			userCalls.merge(row.getUserId(), row.getCalls(), Long::sum);
		}
		for (WealthApiUsageEntity row : dao.findTotalsSince(from, only)) {
			Sums sums = totals.get(row.getKind()) == null ? null : totals.get(row.getKind()).computeIfAbsent(row.getName(), n -> new Sums());
			if (sums != null) {
				sums.calls += row.getCalls();
				sums.success += row.getSuccess();
				sums.clientErrors += row.getClientErrors();
				sums.failures += row.getFailures();
				sums.totalMs += row.getTotalMs();
			}
		}
		addLiveFromRedis(totals, from, today, only, userCalls);
		for (Map.Entry<Key, LongAdder> entry : buffer.entrySet()) {
			Key key = entry.getKey();
			if (!key.day().isBefore(from)) {
				addField(totals, key, entry.getValue().sum(), only, userCalls);
			}
		}
		ApiUsageDto dto = new ApiUsageDto();
		dto.setDays(days);
		dto.setUserId(only);
		dto.setUsers(usersOf(userCalls));
		dto.setSavedAt(savedAt);
		dto.setBss(rowsOf(totals.get(WealthServiceConstants.USAGE_BSS)));
		dto.setBff(rowsOf(totals.get(WealthServiceConstants.USAGE_BFF)));
		dto.setExternal(rowsOf(totals.get(WealthServiceConstants.USAGE_EXTERNAL)));
		dto.setJobs(rowsOf(totals.get(WealthServiceConstants.USAGE_JOB)));
		LOGGER.info(WealthServiceConstants.EXITING + methodName);
		return dto;
	}

	// "" for all users, or one user's id; "me" is the user who is asking. A bad id is refused.
	private static String userFilter(ApiUsageRequestDto request) {
		String asked = request == null || request.getUserId() == null ? "" : request.getUserId().trim();
		if (asked.isEmpty()) {
			return "";
		}
		if (WealthServiceConstants.USAGE_USER_ME.equalsIgnoreCase(asked)) {
			asked = RequestHeaderUtil.getUserId();
		}
		if (asked == null || !USER_ID.matcher(asked).matches()) {
			throw new KarthiException(BaseError.INVALID_REQUEST);
		}
		return asked.toLowerCase();
	}

	// Everyone with website calls in the period, most first, named from the users table.
	private List<ApiUsageUserDto> usersOf(Map<String, Long> userCalls) {
		Map<String, String> emails = new LinkedHashMap<>();
		for (WealthApiUsageEntity user : dao.findUsers()) {
			emails.put(user.getUserId().toLowerCase(), user.getEmail());
		}
		String caller = userOf(RequestHeaderUtil.getUserId());
		List<ApiUsageUserDto> users = new ArrayList<>();
		for (Map.Entry<String, Long> entry : userCalls.entrySet()) {
			ApiUsageUserDto user = new ApiUsageUserDto();
			user.setUserId(entry.getKey());
			user.setEmail(emails.getOrDefault(entry.getKey(), ""));
			user.setCalls(entry.getValue());
			user.setMe(entry.getKey().equals(caller));
			users.add(user);
		}
		users.sort(Comparator.comparingLong(ApiUsageUserDto::getCalls).reversed().thenComparing(ApiUsageUserDto::getEmail));
		return users;
	}

	// The counts in Redis that the database does not have yet: today's and yesterday's hashes, and moved-aside batches that are not saved.
	private void addLiveFromRedis(Map<String, Map<String, Sums>> totals, LocalDate from, LocalDate today, String only, Map<String, Long> userCalls) {
		if (!useRedis()) {
			return;
		}
		try {
			for (LocalDate day : List.of(today, today.minusDays(1))) {
				if (day.isBefore(from)) {
					continue;
				}
				for (String kind : KINDS) {
					addHash(totals, kind, redis.opsForHash().entries(hashKey(day, kind)), day, only, userCalls);
				}
			}
			Set<String> pending = redis.opsForSet().members(WealthServiceConstants.USAGE_PENDING_KEY);
			if (pending != null) {
				for (String moved : pending) {
					String[] parts = moved.split(":");
					if (parts.length >= 6 && !LocalDate.parse(parts[3], DAY).isBefore(from) && !dao.isBatchSaved(moved)) {
						addHash(totals, parts[4], redis.opsForHash().entries(moved), LocalDate.parse(parts[3], DAY), only, userCalls);
					}
				}
			}
		} catch (RuntimeException e) {
			LOGGER.warn("API usage: the unsaved counts in Redis could not be read (" + e.getClass().getSimpleName() + ")");
		}
	}

	private void addHash(Map<String, Map<String, Sums>> totals, String kind, Map<Object, Object> entries, LocalDate day, String only, Map<String, Long> userCalls) {
		for (Map.Entry<Object, Object> entry : entries.entrySet()) {
			String[] one = split(entry.getKey().toString());
			if (one != null) {
				addField(totals, new Key(day, kind, one[0], one[1], one[2]), Long.parseLong(entry.getValue().toString()), only, userCalls);
			}
		}
	}

	// A count is added to the totals when it is the chosen user's (or no user is chosen). Each user's website calls are added up for the user choice either way.
	private void addField(Map<String, Map<String, Sums>> totals, Key key, long value, String only, Map<String, Long> userCalls) {
		if (WealthServiceConstants.USAGE_BFF.equals(key.kind()) && CALLS.equals(key.field()) && !WealthServiceConstants.USAGE_NO_USER.equals(key.user())) {
			userCalls.merge(key.user(), value, Long::sum);
		}
		if (!only.isEmpty() && !only.equals(key.user())) {
			return;
		}
		Map<String, Sums> byName = totals.get(key.kind());
		if (byName == null) {
			return;
		}
		Sums sums = byName.computeIfAbsent(key.name(), n -> new Sums());
		switch (key.field()) {
		case CALLS -> sums.calls += value;
		case SUCCESS -> sums.success += value;
		case CLIENT -> sums.clientErrors += value;
		case FAILED -> sums.failures += value;
		case MILLIS -> sums.totalMs += value;
		default -> {
		}
		}
	}

	private List<ApiUsageRowDto> rowsOf(Map<String, Sums> byName) {
		List<ApiUsageRowDto> rows = new ArrayList<>();
		for (Map.Entry<String, Sums> entry : byName.entrySet()) {
			Sums sums = entry.getValue();
			if (sums.calls <= 0) {
				continue;
			}
			ApiUsageRowDto row = new ApiUsageRowDto();
			row.setName(entry.getKey());
			row.setCalls(sums.calls);
			row.setSuccess(sums.success);
			row.setClientErrors(sums.clientErrors);
			row.setFailures(sums.failures);
			row.setAverageMs(Math.round((double) sums.totalMs / sums.calls));
			rows.add(row);
		}
		rows.sort(Comparator.comparingLong(ApiUsageRowDto::getCalls).reversed().thenComparing(ApiUsageRowDto::getName));
		return rows;
	}
}
