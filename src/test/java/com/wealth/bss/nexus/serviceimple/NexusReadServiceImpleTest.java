package com.wealth.bss.nexus.serviceimple;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wealth.bss.config.KafkaEndpoint;
import com.wealth.bss.config.RedisEndpoint;
import com.wealth.bss.nexus.service.IfNexusEventService;
import com.wealth.common.model.dto.CachedValueDto;
import com.wealth.common.utils.generic.RequestHeaderUtil;

// The nexus cache underneath is the real one with Redis switched off (the internal cache), so keys, locks and expiry behave as they do in the BSS.
class NexusReadServiceImpleTest {

	private static final String USER = "2a7014a8-9cf0-4d39-bed2-c83a83eddf0c";
	private static final String OTHER = "9b1d4f1e-0000-4000-8000-000000000001";

	private final AtomicLong now = new AtomicLong(1_000_000);
	private final AtomicInteger loads = new AtomicInteger();
	private IfNexusEventService events;
	private KafkaEndpoint kafka;
	private ExecutorService executor;
	private NexusReadServiceImple nexus;
	private Consumer<String> handler;
	private volatile String value = "v1";

	@SuppressWarnings("unchecked")
	@BeforeEach
	void setUp() {
		RedisEndpoint redisEndpoint = mock(RedisEndpoint.class);
		when(redisEndpoint.isEnabled()).thenReturn(false);
		NexusCacheServiceImple cache = new NexusCacheServiceImple(mock(StringRedisTemplate.class), redisEndpoint, now::get);
		events = mock(IfNexusEventService.class);
		kafka = mock(KafkaEndpoint.class);
		when(kafka.refreshSeconds()).thenReturn(10);
		when(kafka.isEnabled()).thenReturn(false);
		executor = Executors.newSingleThreadExecutor();
		nexus = new NexusReadServiceImple(cache, events, kafka, new ObjectMapper(), now::get, executor);
		ArgumentCaptor<Consumer<String>> captured = ArgumentCaptor.forClass(Consumer.class);
		verify(events).onRefresh(captured.capture());
		handler = captured.getValue();
		RequestHeaderUtil.setUserId(USER);
	}

	@AfterEach
	void tearDown() {
		executor.shutdownNow();
		RequestHeaderUtil.clear();
	}

	private String load() {
		loads.incrementAndGet();
		return value;
	}

	private CachedValueDto<String> read() {
		return nexus.read("totals", String.class, this::load);
	}

	// Waits for the refresh work already handed to the background thread.
	private void settle() throws Exception {
		executor.submit(() -> {
		}).get();
	}

	@Test
	void aMissingValueIsReadOnceStoredAndThenServedFreshWithoutTouchingTheDatabase() {
		CachedValueDto<String> first = read();
		now.addAndGet(9_000);
		CachedValueDto<String> second = read();

		assertEquals("v1", first.getData());
		assertFalse(first.isStale());
		assertEquals("v1", second.getData());
		assertFalse(second.isStale());
		assertEquals(1, loads.get());
	}

	@Test
	void aStaleValueIsReturnedAtOnceAndRefreshedInsideTheBssWhenKafkaIsOff() throws Exception {
		read();
		value = "v2";
		now.addAndGet(11_000);

		CachedValueDto<String> stale = read();
		settle();

		assertEquals("v1", stale.getData(), "the old value is returned now");
		assertTrue(stale.isStale());
		assertEquals(2, loads.get());
		CachedValueDto<String> after = read();
		assertEquals("v2", after.getData());
		assertFalse(after.isStale());
		verify(events, never()).publishRefresh(anyString(), anyString());
	}

	@Test
	void manyStaleReadsMakeOneRefreshNotMany() throws Exception {
		read();
		now.addAndGet(11_000);

		for (int i = 0; i < 5; i++) {
			read();
		}
		settle();

		assertEquals(2, loads.get(), "the first read plus one refresh");
	}

	@Test
	void whenKafkaIsActiveAndTakesTheMessageTheBssDoesNotRefreshByItselfUntilTheConsumerGetsIt() throws Exception {
		when(kafka.isEnabled()).thenReturn(true);
		when(kafka.isConfigured()).thenReturn(true);
		when(events.publishRefresh(anyString(), anyString())).thenReturn(true);
		read();
		value = "v2";
		now.addAndGet(11_000);

		read();
		settle();

		assertEquals(1, loads.get(), "nothing was read until the consumer handled the request");
		ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
		verify(events).publishRefresh(key.capture(), payload.capture());
		assertEquals("wealth:nexus-totals:" + USER, key.getValue());
		assertFalse(payload.getValue().contains("password"));

		handler.accept(payload.getValue());

		assertEquals(2, loads.get());
		assertEquals("v2", read().getData());
	}

	@Test
	void whenKafkaIsActiveButDoesNotTakeTheMessageTheBssRefreshesByItself() throws Exception {
		when(kafka.isEnabled()).thenReturn(true);
		when(kafka.isConfigured()).thenReturn(true);
		when(events.publishRefresh(anyString(), anyString())).thenReturn(false);
		read();
		now.addAndGet(11_000);

		read();
		settle();

		assertEquals(2, loads.get());
	}

	@Test
	void aRefreshRequestedBeforeTheStoredValueWasReadIsSkippedUnlessItIsForced() {
		read();
		String old = "{\"name\":\"totals\",\"userId\":\"" + USER + "\",\"forced\":false,\"requestedAt\":1}";
		String forced = "{\"name\":\"totals\",\"userId\":\"" + USER + "\",\"forced\":true,\"requestedAt\":1}";

		handler.accept(old);
		assertEquals(1, loads.get(), "the stored value is newer than the request");

		handler.accept(forced);
		assertEquals(2, loads.get());
		assertEquals(USER, RequestHeaderUtil.getUserId(), "the caller's own user is left as it was");
	}

	@Test
	void aRefreshForAReadNobodyAskedForSinceTheRestartIsIgnored() {
		handler.accept("{\"name\":\"never-read\",\"userId\":\"" + USER + "\",\"forced\":true,\"requestedAt\":1}");

		assertEquals(0, loads.get());
	}

	@Test
	void theHardRefreshIgnoresTheAgeAndIsLimitedToOnceEveryFewSecondsPerUser() throws Exception {
		read();
		value = "v2";

		CachedValueDto<String> now1 = nexus.hardRefresh("totals", String.class, this::load);
		settle();
		assertEquals("v1", now1.getData(), "the value as it is now; the refresh lands a moment later");
		assertTrue(now1.isStale());
		assertEquals(2, loads.get());

		nexus.hardRefresh("totals", String.class, this::load);
		settle();
		assertEquals(2, loads.get(), "a second click inside 5 seconds does nothing");

		now.addAndGet(5_001);
		nexus.hardRefresh("totals", String.class, this::load);
		settle();
		assertEquals(3, loads.get());
	}

	@Test
	void aFailingRefreshKeepsTheOldValueAndTheNextStaleReadAsksAgain() throws Exception {
		read();
		now.addAndGet(11_000);
		java.util.function.Supplier<String> failing = () -> {
			loads.incrementAndGet();
			throw new IllegalStateException("database down");
		};

		CachedValueDto<String> stale = nexus.read("totals", String.class, failing);
		settle();

		assertEquals("v1", stale.getData());
		now.addAndGet(5_001);
		assertEquals("v1", nexus.read("totals", String.class, failing).getData(), "still the old value");
		settle();
		assertEquals(3, loads.get(), "asked again after the short lock");
	}

	@Test
	void everyUserHasTheirOwnKeyAndAnEvictionDropsOnlyTheirs() {
		read();
		RequestHeaderUtil.setUserId(OTHER);
		value = "v-other";
		CachedValueDto<String> other = read();
		RequestHeaderUtil.setUserId(USER);

		assertEquals("v-other", other.getData());
		assertEquals("v1", read().getData(), "the first user still sees their own value");
		assertEquals(2, loads.get());

		nexus.evict("totals", USER);
		value = "v-new";
		assertEquals("v-new", read().getData());
		RequestHeaderUtil.setUserId(OTHER);
		assertEquals("v-other", read().getData());
	}

	@Test
	void withoutASignedInUserTheLoaderAnswersAndNothingIsCached() {
		RequestHeaderUtil.clear();

		assertEquals("v1", nexus.read("totals", String.class, this::load).getData());
		assertEquals("v1", nexus.read("totals", String.class, this::load).getData());

		assertEquals(2, loads.get());
		verify(events, never()).publishRefresh(anyString(), anyString());
	}

	private static final java.time.Duration TTL = java.time.Duration.ofSeconds(60);
	private static final com.fasterxml.jackson.core.type.TypeReference<java.util.List<String>> LIST = new com.fasterxml.jackson.core.type.TypeReference<>() {
	};

	private java.util.List<String> loadList() {
		loads.incrementAndGet();
		return java.util.List.of(value, "x");
	}

	@Test
	void aRememberedReadIsLoadedOnceAndKeptPerUserAndVariant() throws Exception {
		assertEquals(java.util.List.of("v1", "x"), nexus.remember("summary", "a", TTL, LIST, this::loadList));
		settle();
		assertEquals(java.util.List.of("v1", "x"), nexus.remember("summary", "a", TTL, LIST, this::loadList));
		assertEquals(1, loads.get(), "the second call is answered from the cache");

		nexus.remember("summary", "b", TTL, LIST, this::loadList);
		assertEquals(2, loads.get(), "another variant is another copy");

		RequestHeaderUtil.setUserId(OTHER);
		nexus.remember("summary", "a", TTL, LIST, this::loadList);
		assertEquals(3, loads.get(), "another user never sees this user's copy");
	}

	@Test
	void aRememberedReadIsStoredInTheBackgroundAndAStoreFailureNeverReachesTheCaller() throws Exception {
		java.util.concurrent.CountDownLatch gate = new java.util.concurrent.CountDownLatch(1);
		executor.execute(() -> {
			try {
				gate.await();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		});

		assertEquals(java.util.List.of("v1", "x"), nexus.remember("summary", "a", TTL, LIST, this::loadList));
		assertEquals(1, loads.get(), "the caller got the answer while the store was still waiting");
		gate.countDown();
		settle();
		nexus.remember("summary", "a", TTL, LIST, this::loadList);
		assertEquals(1, loads.get(), "once stored, the next call is a hit");
	}

	@Test
	void evictingAUsersReadDropsEveryRememberedVariantOfThatUserOnly() throws Exception {
		nexus.remember("summary", "a", TTL, LIST, this::loadList);
		nexus.remember("summary", "b", TTL, LIST, this::loadList);
		RequestHeaderUtil.setUserId(OTHER);
		nexus.remember("summary", "a", TTL, LIST, this::loadList);
		settle();
		assertEquals(3, loads.get());

		now.addAndGet(5);
		nexus.evict("summary", USER);

		RequestHeaderUtil.setUserId(USER);
		value = "v2";
		assertEquals(java.util.List.of("v2", "x"), nexus.remember("summary", "a", TTL, LIST, this::loadList));
		nexus.remember("summary", "b", TTL, LIST, this::loadList);
		assertEquals(5, loads.get(), "both variants of the evicted user load again");
		settle();
		RequestHeaderUtil.setUserId(OTHER);
		nexus.remember("summary", "a", TTL, LIST, this::loadList);
		assertEquals(5, loads.get(), "the other user's copy stays");
	}

	@Test
	void aRememberedReadExpiresAfterItsTimeToLive() throws Exception {
		nexus.remember("summary", "a", TTL, LIST, this::loadList);
		settle();
		now.addAndGet(61_000);

		nexus.remember("summary", "a", TTL, LIST, this::loadList);

		assertEquals(2, loads.get());
	}

	@Test
	void aSharedReadIsOneCopyForEveryoneUntilItIsDropped() {
		nexus.rememberShared("nav", "user", TTL, LIST, this::loadList);
		RequestHeaderUtil.setUserId(OTHER);
		nexus.rememberShared("nav", "user", TTL, LIST, this::loadList);
		nexus.rememberShared("nav", "admin", TTL, LIST, this::loadList);
		assertEquals(2, loads.get(), "one copy per variant, the same for both users");

		nexus.evictShared("nav", "user");
		nexus.rememberShared("nav", "user", TTL, LIST, this::loadList);
		assertEquals(3, loads.get());
	}

	@Test
	void withoutASignedInUserARememberedReadAlwaysLoads() {
		RequestHeaderUtil.clear();

		nexus.remember("summary", "a", TTL, LIST, this::loadList);
		nexus.remember("summary", "a", TTL, LIST, this::loadList);

		assertEquals(2, loads.get());
	}
}
