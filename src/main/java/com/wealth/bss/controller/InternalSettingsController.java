package com.wealth.bss.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.wealth.bss.contract.IfInternalSettingsContract;
import com.wealth.bss.serviceinterface.IfEnvironmentService;
import com.wealth.common.generic.ApiResponse;
import com.wealth.common.generic.ApiResponseTemplate;
import com.wealth.common.generic.BaseError;
import com.wealth.common.generic.KarthiException;
import com.wealth.common.logger.KarthiLogger;
import com.wealth.common.model.dto.admin.ConnectionDto;
import com.wealth.common.model.dto.admin.ConnectionRequestDto;

@RestController
public class InternalSettingsController implements IfInternalSettingsContract {

	private static final KarthiLogger LOGGER = new KarthiLogger(InternalSettingsController.class);

	private final IfEnvironmentService environmentService;
	private final ApiResponseTemplate responseTemplate;

	public InternalSettingsController(IfEnvironmentService environmentService, ApiResponseTemplate responseTemplate) {
		this.environmentService = environmentService;
		this.responseTemplate = responseTemplate;
	}

	@Override
	public ResponseEntity<ApiResponse<ConnectionDto, Void>> pick(String serviceKey, ConnectionRequestDto request) throws KarthiException {
		LOGGER.info("Entering pick");
		return responseTemplate.apires(environmentService.pickAddress(serviceKey, request), BaseError.Success);
	}
}
