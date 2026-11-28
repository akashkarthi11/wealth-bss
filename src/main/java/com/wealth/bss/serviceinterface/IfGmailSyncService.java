package com.wealth.bss.serviceinterface;

import com.wealth.common.model.dto.admin.GmailSyncRequestDto;
import com.wealth.common.model.dto.admin.GmailSyncStatusDto;

// Reads the caller's Gmail in the background, so a read can take as long as it needs. The request goes to Kafka (event gmail.sync) and comes back to a worker in this BSS; when Kafka is off or
// down the worker is started directly. Where the read is lives on the caller's Gmail connection row (stage, note, result), so a reloaded screen still shows it in progress.
public interface IfGmailSyncService {

	// Starts a read of kind alerts, statements or all. When one is already in progress nothing new starts and its status is returned.
	GmailSyncStatusDto start(GmailSyncRequestDto request);

	// Where the caller's read is: idle before the first one.
	GmailSyncStatusDto status();
}
