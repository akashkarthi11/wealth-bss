package com.wealth.bss.serviceinterface;

import com.wealth.common.model.dto.admin.ProviderRequestDto;
import com.wealth.common.model.dto.admin.ThirdPartyDto;

// Reads the holdings from the broker and writes them to the investment tables (instruments, holdings, prices). Only Angel One for now.
public interface IfBrokerSyncService {

	// Admin only. Fetches now for the provider (angel-one). CREDENTIALS_INCOMPLETE when a login value is missing, BROKER_FETCH_FAILED when the broker refuses or cannot be reached.
	// The row's last-fetch note is saved either way; the row is returned.
	ThirdPartyDto fetchNow(ProviderRequestDto request);

	// The daily run: every enabled Angel One row with the daily fetch on and an owner. A failure is logged and noted on the row, never thrown.
	void runDaily();
}
