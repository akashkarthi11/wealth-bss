package com.wealth.bss.nexus.service;

import java.util.function.BooleanSupplier;

import com.wealth.common.model.dto.admin.ApiUsageDto;
import com.wealth.common.model.dto.admin.ApiUsageRequestDto;
import com.wealth.common.model.dto.admin.UsageBatchDto;

// Counts how often our own APIs (BSS, and BFF as reported by the BFF), the outside APIs we call (external) and the scheduled jobs (job) are used. The counts
// are kept in memory, added to Redis every few seconds and saved to the database every few minutes. Counting never fails or slows a call.
public interface IfApiUsageService {

	// One outgoing call to an outside provider. endpoint is a short name (login, holdings, test), never an address. Made inside a scheduled job (runJob),
	// it is listed apart as "<provider> <endpoint> (job <name>)". Never throws.
	void recordExternal(String provider, String endpoint, boolean ok, long millis);

	// Runs a scheduled job and counts the run (work returns true when it worked; calls = runs, success = worked, failures = failed, with its time).
	// An exception from the work is counted as failed and passed on. Every scheduled job runs inside this, so job usage is complete.
	void runJob(String name, BooleanSupplier work);

	// Service to service: the BFF's API counts since its last report, shown to the BSS with the shared service key. Returns the number of names taken.
	// SERVICE_KEY_REJECTED without the key; INVALID_REQUEST for a report with more than 500 names, a bad name or a count out of range (nothing is taken).
	int recordFromBff(String serviceKey, UsageBatchDto batch);

	// Admin only: the calls per API and per outside call and the job runs over the last request.days days (1 to 30, default 7), today included, with the
	// counts not saved yet.
	ApiUsageDto list(ApiUsageRequestDto request);
}
