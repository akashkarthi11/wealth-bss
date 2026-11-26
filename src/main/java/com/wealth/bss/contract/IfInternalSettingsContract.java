package com.wealth.bss.contract;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;

import com.wealth.bss.constants.WealthServiceConstants;
import com.wealth.common.generic.ApiResponse;
import com.wealth.common.generic.KarthiException;
import com.wealth.common.model.dto.admin.ConnectionDto;
import com.wealth.common.model.dto.admin.ConnectionRequestDto;

// Service to service, not for users: no token, the caller shows the shared service key (WEALTH_SERVICE_KEY). Only the two service addresses.
@RequestMapping(WealthServiceConstants.INTERNAL_SETTINGS_BASE)
public interface IfInternalSettingsContract {

	@PostMapping(WealthServiceConstants.INTERNAL_PICK)
	ResponseEntity<ApiResponse<ConnectionDto, Void>> pick(@RequestHeader(value = WealthServiceConstants.HEADER_SERVICE_KEY, required = false) String serviceKey,
			@RequestBody ConnectionRequestDto request) throws KarthiException;
}
