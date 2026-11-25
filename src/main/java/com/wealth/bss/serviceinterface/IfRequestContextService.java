package com.wealth.bss.serviceinterface;

import com.wealth.common.client.NexusServiceClient;

public interface IfRequestContextService extends NexusServiceClient {

	// Call after a role change, logout or password reset so the next request reloads from the database.
	void evict(String userId);
}
