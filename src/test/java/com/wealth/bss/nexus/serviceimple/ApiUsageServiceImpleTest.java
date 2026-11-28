package com.wealth.bss.nexus.serviceimple;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.wealth.bss.common.model.dao.IfWealthApiUsageDao;
import com.wealth.bss.common.model.entity.WealthApiUsageEntity;
import com.wealth.bss.config.RedisEndpoint;
import com.wealth.bss.serviceinterface.IfRequestContextService;
import com.wealth.common.generic.BaseError;
import com.wealth.common.generic.KarthiException;
import com.wealth.common.model.dto.admin.ApiUsageDto;
import com.wealth.common.model.dto.admin.ApiUsageRequestDto;
import com.wealth.common.model.dto.admin.ApiUsageRowDto;
import com.wealth.common.utils.generic.RequestHeaderUtil;

class ApiUsageServiceImpleTest {

	private static final String TODAY_HASH = "wealth:usage:20261127:bss";
	private static final String PENDING = "wealth:usage:pending";

	private StringRedisTemplate redis;
	private HashOperations<String, Object, Object> hash;
	private SetOperations<String, String> set;
	private RedisEndpoint endpoint;
	private IfWealthApiUsageDao dao;
	private IfRequestContextService context;
	private Clock clock;
	private ApiUsageServiceImple service;

	@SuppressWarnings("unchecked")
	@BeforeEach
	void setUp() {
		redis = mock(StringRedisTemplate.class);
		hash = mock(HashOperations.class);
		set = mock(SetOperations.class);
		doReturn(hash).when(redis).opsForHash();
		doReturn(set).when(redis).opsForSet();
		endpoint = mock(RedisEndpoint.class);
		when(endpoint.isEnabled()).thenReturn(true);
		dao = mock(IfWealthApiUsageDao.class);
		when(dao.saveBatch(anyString(), anyList())).thenReturn(true);
		context = mock(IfRequestContextService.class);
		clock = Clock.fixed(Instant.parse("2026-11-27T10:00:00Z"), ZoneOffset.UTC);
		service = new ApiUsageServiceImple(redis, endpoint, dao, context, "the-service-key", clock);
	}

	private ApiUsageRequestDto days(Integer days) {
		ApiUsageRequestDto request = new ApiUsageRequestDto();
		request.setDays(days);
		return request;
	}

	private ApiUsageRowDto find(List<ApiUsageRowDto> rows, String name) {
		return rows.stream().filter(row -> row.getName().equals(name)).findFirst().orElseThrow();
	}

	@Test
	void everyFinishedRequestIsCountedOnceByRouteTemplateAndResultClass() {
		when(endpoint.isEnabled()).thenReturn(false);
		service.record("POST", "/wealth-service/api/v1/accounts/detail", 200, 10);
		service.record("POST", "/wealth-service/api/v1/accounts/detail", 404, 20);
		service.record("POST", "/wealth-service/api/v1/accounts/detail", 500, 30);
		service.record("POST", "/wealth-service/api/v1/accounts/detail", 302, 0);

		ApiUsageRowDto row = find(service.list(days(1)).getBss(), "POST /wealth-service/api/v1/accounts/detail");

		assertEquals(4, row.getCalls());
		assertEquals(2, row.getSuccess(), "2xx and 3xx are successes");
		assertEquals(1, row.getClientErrors());
		assertEquals(1, row.getFailures());
		assertEquals(15, row.getAverageMs(), "60 ms over 4 calls");
	}

	@Test
	void preflightProbesAndTheUsageScreenItselfAreNotCountedAndAnUnknownPathIsOneName() {
		when(endpoint.isEnabled()).thenReturn(false);
		service.record("OPTIONS", "/wealth-service/api/v1/accounts/list", 200, 1);
		service.record("GET", "/actuator/health", 200, 1);
		service.record("POST", "/wealth-service/api/v1/advisor/api-usage/list", 200, 1);
		service.record("GET", null, 404, 1);
		service.record("GET", null, 404, 1);

		ApiUsageDto usage = service.list(days(1));

		assertEquals(1, usage.getBss().size());
		assertEquals("UNMATCHED", usage.getBss().get(0).getName());
		assertEquals(2, usage.getBss().get(0).getCalls());
	}

	@Test
	void outsideCallsAreCountedPerProviderAndEndpointWithOkAndFailedApart() {
		when(endpoint.isEnabled()).thenReturn(false);
		service.recordExternal("angel-one", "login", true, 100);
		service.recordExternal("angel-one", "login", false, 300);
		service.recordExternal("angel-one", "holdings", true, 50);

		ApiUsageDto usage = service.list(days(1));
		ApiUsageRowDto login = find(usage.getExternal(), "angel-one login");

		assertEquals(2, login.getCalls());
		assertEquals(1, login.getSuccess());
		assertEquals(1, login.getFailures());
		assertEquals(200, login.getAverageMs());
		assertEquals(1, find(usage.getExternal(), "angel-one holdings").getCalls());
		assertTrue(usage.getBss().isEmpty());
	}

	@Test
	void countingNeverThrowsWhateverItIsGiven() {
		service.record(null, null, 0, -5);
		service.record("GET", "/x", 999, Long.MAX_VALUE);
		service.recordExternal(null, null, true, -1);
	}

	@Test
	void drainAddsTheCountsToRedisInAtomicIncrementsThenEmptiesTheMemory() {
		service.record("POST", "/a", 200, 25);

		service.drain();
		service.drain();

		verify(hash).increment(TODAY_HASH, "POST /a|-|calls", 1L);
		verify(hash).increment(TODAY_HASH, "POST /a|-|ok", 1L);
		verify(hash).increment(TODAY_HASH, "POST /a|-|ms", 25L);
		verify(hash, times(3)).increment(anyString(), anyString(), org.mockito.ArgumentMatchers.anyLong());
		verify(redis).expire(TODAY_HASH, Duration.ofDays(8));
	}

	@Test
	void aFailedDrainPutsTheCountsBackAndNeverThrows() {
		doThrow(new IllegalStateException("redis down")).when(hash).increment(anyString(), anyString(), org.mockito.ArgumentMatchers.anyLong());
		service.record("POST", "/a", 200, 25);

		service.drain();

		assertEquals(1, find(service.list(days(1)).getBss(), "POST /a").getCalls(), "the count is still there");
	}

	@Test
	void withRedisOffTheCountsAreSavedStraightFromMemoryWithoutTouchingRedis() {
		when(endpoint.isEnabled()).thenReturn(false);
		service.record("POST", "/a", 200, 10);
		service.record("POST", "/a", 200, 30);

		service.save();

		ArgumentCaptor<String> batch = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<List<WealthApiUsageEntity>> rows = ArgumentCaptor.forClass(List.class);
		verify(dao).saveBatch(batch.capture(), rows.capture());
		assertTrue(batch.getValue().startsWith("mem:"));
		WealthApiUsageEntity row = rows.getValue().get(0);
		assertEquals(LocalDate.of(2026, 11, 27), row.getDay());
		assertEquals("bss", row.getKind());
		assertEquals("POST /a", row.getName());
		assertEquals(2, row.getCalls());
		assertEquals(2, row.getSuccess());
		assertEquals(40, row.getTotalMs());
		verify(redis, never()).opsForHash();
		assertTrue(service.list(days(1)).getBss().isEmpty(), "what was saved is gone from memory, so the screen does not count it twice");
	}

	private String moveAsideFor(String key) {
		when(redis.hasKey(key)).thenReturn(true);
		return "wealth:usage:flush:20261127:bss:" + clock.millis();
	}

	@Test
	void withRedisOnTheHashIsMovedAsideSavedOnceAndDeleted() {
		String moved = moveAsideFor(TODAY_HASH);
		doReturn(Set.of(moved)).when(set).members(PENDING);
		doReturn(Map.of("POST /a|calls", "5", "POST /a|ok", "4", "POST /a|4xx", "1", "POST /a|ms", "100")).when(hash).entries(moved);

		service.save();

		verify(redis).rename(TODAY_HASH, moved);
		verify(set).add(PENDING, moved);
		ArgumentCaptor<List<WealthApiUsageEntity>> rows = ArgumentCaptor.forClass(List.class);
		verify(dao).saveBatch(eq(moved), rows.capture());
		WealthApiUsageEntity row = rows.getValue().get(0);
		assertEquals(5, row.getCalls());
		assertEquals(4, row.getSuccess());
		assertEquals(1, row.getClientErrors());
		assertEquals(100, row.getTotalMs());
		verify(redis).delete(moved);
		verify(set).remove(PENDING, moved);
	}

	@Test
	void aFailedDatabaseWriteKeepsTheMovedKeyForTheNextSave() {
		String moved = moveAsideFor(TODAY_HASH);
		doReturn(Set.of(moved)).when(set).members(PENDING);
		doReturn(Map.of("POST /a|calls", "5")).when(hash).entries(moved);
		when(dao.saveBatch(eq(moved), anyList())).thenThrow(new IllegalStateException("database down"));

		service.save();

		verify(redis, never()).delete(moved);
		verify(set, never()).remove(PENDING, moved);
	}

	@Test
	void aBatchTheDatabaseAlreadyHasIsNotAddedAgainAndTheKeyIsCleared() {
		String moved = moveAsideFor(TODAY_HASH);
		doReturn(Set.of(moved)).when(set).members(PENDING);
		doReturn(Map.of("POST /a|calls", "5")).when(hash).entries(moved);
		when(dao.saveBatch(eq(moved), anyList())).thenReturn(false);

		service.save();

		verify(redis).delete(moved);
	}

	@Test
	void theScreenAddsSavedTotalsRedisCountsAndMemoryTogether() {
		WealthApiUsageEntity saved = new WealthApiUsageEntity();
		saved.setKind("bss");
		saved.setName("GET /x");
		saved.setCalls(10);
		saved.setSuccess(9);
		saved.setFailures(1);
		saved.setTotalMs(1000);
		when(dao.findTotalsSince(LocalDate.of(2026, 11, 21), "")).thenReturn(List.of(saved));
		doReturn(Map.of("GET /x|calls", "2", "GET /x|ok", "2", "GET /x|ms", "40")).when(hash).entries(TODAY_HASH);
		service.record("GET", "/x", 200, 10);
		// the memory counts are added once; the same name is not listed twice

		ApiUsageRowDto row = find(service.list(days(7)).getBss(), "GET /x");

		assertEquals(13, row.getCalls());
		assertEquals(12, row.getSuccess());
		assertEquals(1, row.getFailures());
		assertEquals(81, row.getAverageMs(), "1050 ms over 13 calls");
	}

	@Test
	void theScreenIsForAnAdminAndTheDaysAreChecked() {
		assertEquals(BaseError.INVALID_REQUEST, assertThrows(KarthiException.class, () -> service.list(days(0))).getMessage());
		assertEquals(BaseError.INVALID_REQUEST, assertThrows(KarthiException.class, () -> service.list(days(31))).getMessage());

		doThrow(new KarthiException(BaseError.ADMIN_ONLY_ACTION)).when(context).requireAdmin();
		assertEquals(BaseError.ADMIN_ONLY_ACTION, assertThrows(KarthiException.class, () -> service.list(days(7))).getMessage());
		verify(dao, never()).findTotalsSince(any(), any());
	}

	// ---- the BFF's counts, jobs, and outside calls made by a job ----

	private com.wealth.common.model.dto.admin.UsageCountDto count(String name, long calls, long success, long client, long failures, long ms) {
		com.wealth.common.model.dto.admin.UsageCountDto one = new com.wealth.common.model.dto.admin.UsageCountDto();
		one.setName(name);
		one.setCalls(calls);
		one.setSuccess(success);
		one.setClientErrors(client);
		one.setFailures(failures);
		one.setTotalMs(ms);
		return one;
	}

	private com.wealth.common.model.dto.admin.UsageBatchDto batch(com.wealth.common.model.dto.admin.UsageCountDto... counts) {
		com.wealth.common.model.dto.admin.UsageBatchDto batch = new com.wealth.common.model.dto.admin.UsageBatchDto();
		batch.setCounts(List.of(counts));
		return batch;
	}

	@Test
	void theBffReportsItsCountsWithTheServiceKeyAndTheyAreListedApartFromTheBss() {
		when(endpoint.isEnabled()).thenReturn(false);
		service.record("POST", "/wealth-service/api/v1/accounts/list", 200, 10);

		assertEquals(1, service.recordFromBff("the-service-key", batch(count("POST /wealth/api/v1/accounts/list", 4, 3, 1, 0, 400))));

		ApiUsageDto usage = service.list(days(1));
		ApiUsageRowDto bff = find(usage.getBff(), "POST /wealth/api/v1/accounts/list");
		assertEquals(4, bff.getCalls());
		assertEquals(1, bff.getClientErrors());
		assertEquals(100, bff.getAverageMs());
		assertEquals(1, usage.getBss().size(), "the BSS list has only what the BSS served");
	}

	@Test
	void theBffReportIsRefusedWithoutTheRightKeyAndNothingIsTaken() {
		when(endpoint.isEnabled()).thenReturn(false);
		assertEquals(BaseError.SERVICE_KEY_REJECTED, assertThrows(KarthiException.class, () -> service.recordFromBff(null, batch(count("a", 1, 1, 0, 0, 1)))).getMessage());
		assertEquals(BaseError.SERVICE_KEY_REJECTED, assertThrows(KarthiException.class, () -> service.recordFromBff("wrong", batch(count("a", 1, 1, 0, 0, 1)))).getMessage());
		ApiUsageServiceImple noKey = new ApiUsageServiceImple(redis, endpoint, dao, context, "", clock);
		assertEquals(BaseError.SERVICE_KEY_REJECTED, assertThrows(KarthiException.class, () -> noKey.recordFromBff("", batch(count("a", 1, 1, 0, 0, 1)))).getMessage());

		assertTrue(service.list(days(1)).getBff().isEmpty());
	}

	@Test
	void aBadBffReportIsRefusedWholeSoABadSenderCannotFillTheMemory() {
		String key = "the-service-key";
		assertEquals(BaseError.INVALID_REQUEST, assertThrows(KarthiException.class, () -> service.recordFromBff(key, null)).getMessage());
		assertEquals(BaseError.INVALID_REQUEST, assertThrows(KarthiException.class, () -> service.recordFromBff(key, batch(count(" ", 1, 1, 0, 0, 1)))).getMessage());
		assertEquals(BaseError.INVALID_REQUEST, assertThrows(KarthiException.class, () -> service.recordFromBff(key, batch(count("x".repeat(201), 1, 1, 0, 0, 1)))).getMessage());
		assertEquals(BaseError.INVALID_REQUEST, assertThrows(KarthiException.class, () -> service.recordFromBff(key, batch(count("a", -1, 0, 0, 0, 0)))).getMessage());
		assertEquals(BaseError.INVALID_REQUEST, assertThrows(KarthiException.class, () -> service.recordFromBff(key, batch(count("a", 1, 1, 0, 0, 5_000_000_000L)))).getMessage());
		assertEquals(BaseError.INVALID_REQUEST, assertThrows(KarthiException.class, () -> service.recordFromBff(key, batch(count("a", 1, 2, 0, 0, 1)))).getMessage(),
				"more outcomes than calls");
		com.wealth.common.model.dto.admin.UsageCountDto[] tooMany = new com.wealth.common.model.dto.admin.UsageCountDto[501];
		java.util.Arrays.fill(tooMany, count("a", 1, 1, 0, 0, 1));
		assertEquals(BaseError.INVALID_REQUEST, assertThrows(KarthiException.class, () -> service.recordFromBff(key, batch(tooMany))).getMessage());
		// one bad name refuses the whole report: the good one before it is not taken either
		assertEquals(BaseError.INVALID_REQUEST, assertThrows(KarthiException.class, () -> service.recordFromBff(key, batch(count("good", 1, 1, 0, 0, 1), count("", 1, 1, 0, 0, 1)))).getMessage());

		when(endpoint.isEnabled()).thenReturn(false);
		assertTrue(service.list(days(1)).getBff().isEmpty());
	}

	@Test
	void theBffReportsToTheBssRouteAreNotCountedAsBssUsage() {
		when(endpoint.isEnabled()).thenReturn(false);
		service.record("POST", "/wealth-service/api/v1/internal/usage/record", 200, 3);

		assertTrue(service.list(days(1)).getBss().isEmpty());
	}

	@Test
	void aJobRunIsCountedAsOneCallWithItsResultAndTimeAndAFailureIsPassedOn() {
		when(endpoint.isEnabled()).thenReturn(false);

		service.runJob("broker-fetch", () -> true);
		service.runJob("broker-fetch", () -> false);
		assertThrows(IllegalStateException.class, () -> service.runJob("broker-fetch", () -> {
			throw new IllegalStateException("boom");
		}));

		ApiUsageRowDto job = find(service.list(days(1)).getJobs(), "broker-fetch");
		assertEquals(3, job.getCalls(), "three runs");
		assertEquals(1, job.getSuccess());
		assertEquals(2, job.getFailures(), "a refused run and a run that threw");
	}

	@Test
	void anOutsideCallMadeInsideAJobIsListedApartFromTheSameCallMadeByAUser() {
		when(endpoint.isEnabled()).thenReturn(false);
		service.recordExternal("angel-one", "holdings", true, 80);

		service.runJob("broker-fetch", () -> {
			service.recordExternal("angel-one", "holdings", true, 120);
			return true;
		});
		service.recordExternal("angel-one", "holdings", false, 60);

		ApiUsageDto usage = service.list(days(1));
		assertEquals(2, find(usage.getExternal(), "angel-one holdings").getCalls(), "the two calls made outside the job");
		assertEquals(1, find(usage.getExternal(), "angel-one holdings (job broker-fetch)").getCalls());
	}

	// ---- usage by user ----

	private static final String ME = "11111111-1111-1111-1111-111111111111";
	private static final String OTHER = "22222222-2222-2222-2222-222222222222";

	private ApiUsageRequestDto forUser(String userId) {
		ApiUsageRequestDto request = new ApiUsageRequestDto();
		request.setUserId(userId);
		return request;
	}

	private WealthApiUsageEntity person(String id, String email) {
		WealthApiUsageEntity user = new WealthApiUsageEntity();
		user.setUserId(id);
		user.setEmail(email);
		return user;
	}

	@AfterEach
	void clearCaller() {
		RequestHeaderUtil.clear();
	}

	@Test
	void aRequestIsCountedForTheUserWhoMadeItAndAnUnknownCallerForNoUser() {
		RequestHeaderUtil.setUserId(ME.toUpperCase());
		service.record("POST", "/a", 200, 5);
		RequestHeaderUtil.setUserId("not-an-id");
		service.record("POST", "/b", 200, 5);

		service.drain();

		verify(hash).increment(TODAY_HASH, "POST /a|" + ME + "|calls", 1L);
		verify(hash).increment(TODAY_HASH, "POST /b|-|calls", 1L);
	}

	@Test
	void anOutsideCallIsTheUsersButOneMadeInsideAJobHasNoUser() {
		RequestHeaderUtil.setUserId(ME);
		service.recordExternal("angel-one", "test", true, 5);
		service.runJob("broker-fetch", () -> {
			service.recordExternal("angel-one", "holdings", true, 5);
			return true;
		});

		service.drain();

		verify(hash).increment("wealth:usage:20261127:external", "angel-one test|" + ME + "|calls", 1L);
		verify(hash).increment("wealth:usage:20261127:external", "angel-one holdings (job broker-fetch)|-|calls", 1L);
		verify(hash).increment("wealth:usage:20261127:job", "broker-fetch|-|calls", 1L);
	}

	@Test
	void theBffReportsTheUserOfEachCountAndABadUserIdRefusesTheWholeReport() {
		var mine = count("GET /a", 2, 2, 0, 0, 10);
		mine.setUserId(ME);
		var nobody = count("GET /b", 1, 1, 0, 0, 5);
		assertEquals(2, service.recordFromBff("the-service-key", batch(mine, nobody)));
		service.drain();
		verify(hash).increment("wealth:usage:20261127:bff", "GET /a|" + ME + "|calls", 2L);
		verify(hash).increment("wealth:usage:20261127:bff", "GET /b|-|calls", 1L);

		var bad = count("GET /c", 1, 1, 0, 0, 5);
		bad.setUserId("../etc");
		assertEquals(BaseError.INVALID_REQUEST, assertThrows(KarthiException.class, () -> service.recordFromBff("the-service-key", batch(bad))).getMessage());
	}

	@Test
	void theScreenNarrowsTheCountsToOneUserAndStillListsEveryone() {
		RequestHeaderUtil.setUserId(ME);
		when(dao.findUsers()).thenReturn(List.of(person(ME, "me@example.com"), person(OTHER, "other@example.com")));
		WealthApiUsageEntity savedCalls = person(OTHER, null);
		savedCalls.setCalls(7);
		when(dao.findUserCallsSince(LocalDate.of(2026, 11, 21))).thenReturn(List.of(savedCalls));
		doReturn(Map.of("GET /x|" + ME + "|calls", "3", "GET /x|" + ME + "|ok", "3", "GET /x|" + OTHER + "|calls", "4", "GET /x|" + OTHER + "|ok", "4"))
				.when(hash).entries("wealth:usage:20261127:bff");

		ApiUsageDto usage = service.list(forUser("me"));

		verify(dao).findTotalsSince(LocalDate.of(2026, 11, 21), ME);
		assertEquals(ME, usage.getUserId());
		assertEquals(3, find(usage.getBff(), "GET /x").getCalls(), "only my calls");
		assertEquals(2, usage.getUsers().size());
		assertEquals("other@example.com", usage.getUsers().get(0).getEmail(), "most calls first: 7 saved + 4 not saved");
		assertEquals(11, usage.getUsers().get(0).getCalls());
		assertEquals(3, usage.getUsers().get(1).getCalls());
		assertTrue(usage.getUsers().get(1).isMe());
	}

	@Test
	void theUserListHoldsEveryUserAndAUserWithNoCallsShowsZero() {
		RequestHeaderUtil.setUserId(ME);
		when(dao.findUsers()).thenReturn(List.of(person(ME, "me@example.com"), person(OTHER, "other@example.com")));
		doReturn(Map.of("GET /x|" + ME + "|calls", "3", "GET /x|" + ME + "|ok", "3")).when(hash).entries("wealth:usage:20261127:bff");

		ApiUsageDto usage = service.list(forUser("me"));

		assertEquals(2, usage.getUsers().size());
		assertEquals(3, usage.getUsers().get(0).getCalls());
		assertTrue(usage.getUsers().get(0).isMe());
		assertEquals("other@example.com", usage.getUsers().get(1).getEmail());
		assertEquals(0, usage.getUsers().get(1).getCalls());
	}

	@Test
	void theScreenForAllUsersAddsEveryonesCountsAndABadUserIsRefused() {
		doReturn(Map.of("GET /x|" + ME + "|calls", "3", "GET /x|" + OTHER + "|calls", "4")).when(hash).entries("wealth:usage:20261127:bff");

		ApiUsageDto usage = service.list(forUser(""));

		verify(dao).findTotalsSince(LocalDate.of(2026, 11, 21), "");
		assertEquals(7, find(usage.getBff(), "GET /x").getCalls());
		assertEquals("", usage.getUserId());
		assertEquals(BaseError.INVALID_REQUEST, assertThrows(KarthiException.class, () -> service.list(forUser("everyone"))).getMessage());
		assertEquals(BaseError.INVALID_REQUEST, assertThrows(KarthiException.class, () -> service.list(forUser("me"))).getMessage(), "me with nobody signed in");
	}
}
