package com.wealth.bss.serviceinterface;

import com.wealth.common.model.dto.admin.BrokerUserSaveRequestDto;
import com.wealth.common.model.dto.admin.BrokerUserStatusDto;
import com.wealth.common.model.dto.admin.ProviderRequestDto;
import com.wealth.common.model.dto.admin.ThirdPartyDto;

// Reads the holdings from the broker and writes them to the investment tables (instruments, holdings, prices). Only Angel One for now.
public interface IfBrokerSyncService {

	// Admin only. Fetches now for the provider (angel-one). CREDENTIALS_INCOMPLETE when a login value is missing, BROKER_FETCH_FAILED when the broker refuses or cannot be reached.
	// The row's last-fetch note is saved either way; the row is returned.
	ThirdPartyDto fetchNow(ProviderRequestDto request);

	// The signed-in user's own Angel One connection. The login is kept encrypted in the user's own connection row and never returned. A login saved earlier as the shared one is moved to its owner on first use.
	BrokerUserStatusDto userStatus();

	// Saves the entered login values (a blank one keeps the saved value) and the daily-fetch switch. PROVIDER_NOT_FOUND when an admin has not added Angel One; PROVIDER_INVALID
	// for a value that is too long or has a control character.
	BrokerUserStatusDto userSave(BrokerUserSaveRequestDto request);

	// Signs in once with the user's login to check it; the result is kept. CREDENTIALS_INCOMPLETE when a value is missing.
	BrokerUserStatusDto userTest();

	// Fetches the user's own holdings and cash now into their own demat account; the note is kept either way. CREDENTIALS_INCOMPLETE, BROKER_FETCH_FAILED.
	BrokerUserStatusDto userFetch();

	// Removes the user's login; false when there was none.
	boolean userRemove();

	// The daily run: every enabled Angel One row with the daily fetch on and an owner. A failure is logged and noted on the row, never thrown.
	void runDaily();
}
