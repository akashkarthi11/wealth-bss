package com.wealth.bss.serviceinterface;

import com.wealth.common.model.dto.admin.ConnectionDto;
import com.wealth.common.model.dto.admin.ConnectionRequestDto;

// Environment settings an admin can see and change: where Redis and Kafka are. Admin only. A change is used at once, is saved only when the
// new address answers, and returns to the deployed one on restart. Passwords and other credentials stay in the server environment, never shown.
public interface IfEnvironmentService {

	ConnectionDto redisGet();

	// request.url holds host:port.
	ConnectionDto redisUpdate(ConnectionRequestDto request);

	ConnectionDto redisReset();

	// The Active / Inactive switch (request.enabled). Inactive: the cache, the login limits and the sync lock skip Redis. Kept in memory.
	ConnectionDto redisActive(ConnectionRequestDto request);

	ConnectionDto kafkaGet();

	// request.url holds one host:port or a comma-separated list of them.
	ConnectionDto kafkaUpdate(ConnectionRequestDto request);

	ConnectionDto kafkaReset();

	// The Active / Inactive switch (request.enabled). Inactive: the BSS does not connect to Kafka. Kept in memory.
	ConnectionDto kafkaActive(ConnectionRequestDto request);

	// request.refreshSeconds, 5 to 60 (REFRESH_TIME_INVALID otherwise): after this age a cached read is stale and is refreshed. Kept in memory.
	ConnectionDto kafkaRefreshTime(ConnectionRequestDto request);

	// request.url holds host:port; the database name and options stay as deployed.
	ConnectionDto postgresGet();

	ConnectionDto postgresUpdate(ConnectionRequestDto request);

	ConnectionDto postgresReset();

	// Admin: saves the address a service uses (request.key ui_to_bff or bff_to_bss, request.url the address) in wealth_settings.
	ConnectionDto addressSave(ConnectionRequestDto request);

	// A service picking its address when it boots (request.key, request.url = its deployed address): the saved one, or the deployed one saved now.
	// The caller proves itself with the shared service key instead of a user token.
	ConnectionDto pickAddress(String serviceKey, ConnectionRequestDto request);
}
