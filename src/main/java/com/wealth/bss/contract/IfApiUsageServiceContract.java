package com.wealth.bss.contract;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import com.wealth.bss.constants.WealthServiceConstants;
import com.wealth.common.generic.ApiResponse;
import com.wealth.common.generic.KarthiException;
import com.wealth.common.model.dto.admin.ApiUsageDto;
import com.wealth.common.model.dto.admin.ApiUsageRequestDto;

// Admin only. Not public: the interceptor needs a valid token, and the service checks the role.
@RequestMapping(WealthServiceConstants.API_USAGE_BASE)
public interface IfApiUsageServiceContract {

	@PostMapping(WealthServiceConstants.API_USAGE_LIST)
	ResponseEntity<ApiResponse<ApiUsageDto, Void>> list(@RequestBody(required = false) ApiUsageRequestDto request) throws KarthiException;
}
