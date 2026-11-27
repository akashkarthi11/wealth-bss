package com.wealth.bss.nexus.service;

import java.time.Duration;

// The one way the BSS reads and writes cached values, counters and locks (rules 10 and 10a). Redis is used while it is Active and answering; otherwise
// the BSS internal cache answers the same way. A caller never knows which one ran.
public interface IfNexusCacheService {

	// The value, or null when there is none.
	String get(String key);

	void put(String key, String value, Duration ttl);

	// Removes the entry from Redis and from the internal cache; true when one of them had it.
	boolean remove(String key);

	// A fixed-window counter: the first call opens a window of that length and returns 1; calls inside it add one.
	long increment(String key, Duration window);

	// The counter's value, 0 when there is no open window.
	long count(String key);

	// A lock: true for the caller that takes it, false while someone else holds it. It frees itself after the time to live.
	boolean tryLock(String key, Duration ttl);
}
