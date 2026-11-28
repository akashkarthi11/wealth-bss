package com.wealth.bss.nexus.service;

import java.util.function.Consumer;

// The Kafka side of the nexus: refresh requests go to the topic wealth.money-events (header eventType = cache.refresh, message key = the cache key) and
// come back to this BSS through the consumer. Never needed for correctness: when Kafka is off or down the nexus refreshes inside the BSS (rules 10a).
public interface IfNexusEventService {

	// True only when Kafka is Active, configured, this BSS's consumer is connected, and the broker accepted the message. Otherwise false and nothing was sent.
	boolean publishRefresh(String cacheKey, String payload);

	// The handler the consumer calls with the payload of each refresh request it reads.
	void onRefresh(Consumer<String> handler);

	// A request to read one user's Gmail in the background (header eventType = gmail.sync, message key = the user id). Same rule as publishRefresh: false means nothing was sent.
	boolean publishGmailSync(String userId, String payload);

	// The handler the consumer calls with the payload of each Gmail read request it reads.
	void onGmailSync(Consumer<String> handler);
}
