package com.wealth.bss.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.wealth.bss.contract.IfInternalUsageContract;
import com.wealth.bss.nexus.service.IfApiUsageService;
import com.wealth.common.generic.ApiResponse;
import com.wealth.common.generic.ApiResponseTemplate;
import com.wealth.common.generic.BaseError;
import com.wealth.common.generic.KarthiException;
import com.wealth.common.logger.KarthiLogger;
import com.wealth.common.model.dto.admin.UsageBatchDto;

@RestController
public class InternalUsageController implements IfInternalUsageContract {

	private static final KarthiLogger LOGGER = new KarthiLogger(InternalUsageController.class);

	private final IfApiUsageService apiUsageService;
	private final ApiResponseTemplate responseTemplate;

	public InternalUsageController(IfApiUsageService apiUsageService, ApiResponseTemplate responseTemplate) {
		this.apiUsageService = apiUsageService;
		this.responseTemplate = responseTemplate;
	}

	@Override
	public ResponseEntity<ApiResponse<Integer, Void>> record(String serviceKey, UsageBatchDto batch) throws KarthiException {
		LOGGER.info("Entering record usage");
		return responseTemplate.apires(apiUsageService.recordFromBff(serviceKey, batch), BaseError.Success);
	}
}
