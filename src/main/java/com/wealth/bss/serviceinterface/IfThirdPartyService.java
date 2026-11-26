package com.wealth.bss.serviceinterface;

import java.util.List;

import com.wealth.common.model.dto.admin.ProviderRequestDto;
import com.wealth.common.model.dto.admin.ThirdPartyDto;
import com.wealth.common.model.dto.admin.ThirdPartySaveRequestDto;

// The third-party services the app talks to (Settings > Connections). Admin only. A key is kept encrypted and never returned.
public interface IfThirdPartyService {

	List<ThirdPartyDto> list();

	// Adds or changes a provider. An empty apiKey keeps the saved key; a new one is encrypted (SETTINGS_KEY_MISSING when the server has no key to do it).
	ThirdPartyDto save(ThirdPartySaveRequestDto request);

	// One small request to the provider's address; the result is saved on the row and returned.
	ThirdPartyDto test(ProviderRequestDto request);

	void remove(ProviderRequestDto request);
}
