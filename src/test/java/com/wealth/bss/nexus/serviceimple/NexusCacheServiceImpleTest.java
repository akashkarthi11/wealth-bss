package com.wealth.bss.nexus.serviceimple;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.wealth.bss.config.RedisEndpoint;

// Redis while it is Active and answering; the internal cache otherwise (rules 10a). A clock that the test moves stands in for time.
class NexusCacheServiceImpleTest {

	private StringRedisTemplate redis;
	private ValueOperations<String, String> values;
	private RedisEndpoint endpoint;
	private final AtomicLong now = new AtomicLong(1_000_000);
	private NexusCacheServiceImple cache;

	@SuppressWarnings("unchecked")
	@BeforeEach
	void setUp() {
		redis = mock(StringRedisTemplate.class);
		values = mock(ValueOperations.class);
		endpoint = mock(RedisEndpoint.class);
		when(redis.opsForValue()).thenReturn(values);
		when(endpoint.isEnabled()).thenReturn(true);
		cache = new NexusCacheServiceImple(redis, endpoint, now::get);
	}

	private void redisOff() {
		when(endpoint.isEnabled()).thenReturn(false);
	}

	private void redisDown() {
		IllegalStateException down = new IllegalStateException("redis down");
		when(values.get(anyString())).thenThrow(down);
		doThrow(down).when(values).set(anyString(), anyString(), any(Duration.class));
		when(values.increment(anyString())).thenThrow(down);
		when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenThrow(down);
		when(redis.delete(anyString())).thenThrow(down);
	}

	@Test
	void whenRedisIsOnEverythingGoesToRedis() {
		when(values.get("k")).thenReturn("v");
		when(values.increment("c")).thenReturn(1L);
		when(values.setIfAbsent("l", "1", Duration.ofMinutes(1))).thenReturn(true);
		when(redis.delete("k")).thenReturn(true);

		assertEquals("v", cache.get("k"));
		cache.put("k", "v", Duration.ofSeconds(300));
		assertEquals(1, cache.increment("c", Duration.ofSeconds(900)));
		assertTrue(cache.tryLock("l", Duration.ofMinutes(1)));
		assertTrue(cache.remove("k"));

		verify(values).set("k", "v", Duration.ofSeconds(300));
		verify(redis).expire("c", Duration.ofSeconds(900));
	}

	@Test
	void whenRedisIsSwitchedOffTheInternalCacheDoesTheSameJobAndRedisIsNotTouched() {
		redisOff();

		assertNull(cache.get("k"));
		cache.put("k", "v", Duration.ofSeconds(300));
		assertEquals("v", cache.get("k"));
		assertTrue(cache.remove("k"));
		assertNull(cache.get("k"));
		assertFalse(cache.remove("k"));

		verify(redis, never()).opsForValue();
		verify(redis, never()).delete(anyString());
	}

	@Test
	void anInternalValueIsKeptForItsTimeToLiveAndThenGone() {
		redisOff();
		cache.put("k", "v", Duration.ofSeconds(300));

		now.addAndGet(299_000);
		assertEquals("v", cache.get("k"));
		now.addAndGet(2_000);
		assertNull(cache.get("k"));
		assertFalse(cache.remove("k"), "an expired entry does not count");
	}

	@Test
	void anInternalCounterCountsInsideItsWindowAndStartsAgainAfterIt() {
		redisOff();

		assertEquals(0, cache.count("rl"));
		assertEquals(1, cache.increment("rl", Duration.ofSeconds(900)));
		assertEquals(2, cache.increment("rl", Duration.ofSeconds(900)));
		assertEquals(2, cache.count("rl"));
		now.addAndGet(899_000);
		assertEquals(3, cache.increment("rl", Duration.ofSeconds(900)), "the window opened by the first call is not extended");
		now.addAndGet(2_000);
		assertEquals(0, cache.count("rl"));
		assertEquals(1, cache.increment("rl", Duration.ofSeconds(900)));
	}

	@Test
	void anInternalLockIsTakenOnceAndFreesItselfAfterItsTimeToLive() {
		redisOff();

		assertTrue(cache.tryLock("lock", Duration.ofMinutes(30)));
		assertFalse(cache.tryLock("lock", Duration.ofMinutes(30)));
		now.addAndGet(31 * 60_000);
		assertTrue(cache.tryLock("lock", Duration.ofMinutes(30)));
	}

	@Test
	void theInternalCacheIsBoundedAndKeepsWorkingAtItsCap() {
		redisOff();
		for (int i = 0; i < 10_050; i++) {
			cache.put("k" + i, "v", Duration.ofSeconds(100 + i));
		}

		assertNull(cache.get("k0"), "the entry expiring first made room");
		assertEquals("v", cache.get("k10049"));
	}

	@Test
	void whenRedisFailsTheInternalCacheTakesOverWithoutAnErrorAndRedisIsLeftAloneForAWhile() {
		redisDown();

		assertNull(cache.get("k"), "nothing cached yet, and no error reaches the caller");
		cache.put("k", "v", Duration.ofSeconds(300));
		assertEquals("v", cache.get("k"));
		assertTrue(cache.tryLock("l", Duration.ofMinutes(1)));
		assertFalse(cache.tryLock("l", Duration.ofMinutes(1)), "the internal lock holds");
		assertEquals(1, cache.increment("c", Duration.ofSeconds(900)));
		assertEquals(2, cache.increment("c", Duration.ofSeconds(900)));

		verify(values, times(1)).get("k");
		verify(values, never()).increment("c");
	}

	@Test
	void redisIsUsedAgainWhenTheRetryPauseHasPassed() {
		redisDown();
		cache.get("k");
		org.mockito.Mockito.doReturn("from-redis").when(values).get("k");
		assertNull(cache.get("k"), "still paused: the internal cache answers");

		now.addAndGet(10_001);

		assertEquals("from-redis", cache.get("k"));
	}

	@Test
	void anEvictionClearsBothTheRedisEntryAndTheInternalOne() {
		redisOff();
		cache.put("k", "stale", Duration.ofSeconds(300));
		when(endpoint.isEnabled()).thenReturn(true);
		when(redis.delete("k")).thenReturn(false);

		assertTrue(cache.remove("k"), "the internal entry was removed");

		verify(redis).delete("k");
		redisOff();
		assertNull(cache.get("k"));
	}
}
