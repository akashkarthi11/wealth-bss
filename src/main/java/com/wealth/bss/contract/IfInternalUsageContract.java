package com.wealth.bss.contract;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;

import com.wealth.bss.constants.WealthServiceConstants;
import com.wealth.common.generic.ApiResponse;
import com.wealth.common.generic.KarthiException;
import com.wealth.common.model.dto.admin.UsageBatchDto;

// Service to service, not for users: no token, the caller (the BFF) shows the shared service key (WEALTH_SERVICE_KEY). Takes the BFF's API usage counts.
@RequestMapping(WealthServiceConstants.INTERNAL_USAGE_BASE)
public interface IfInternalUsageContract {

	@PostMapping(WealthServiceConstants.INTERNAL_USAGE_RECORD)
	ResponseEntity<ApiResponse<Integer, Void>> record(@RequestHeader(value = WealthServiceConstants.HEADER_SERVICE_KEY, required = false) String serviceKey,
			@RequestBody UsageBatchDto batch) throws KarthiException;
}
