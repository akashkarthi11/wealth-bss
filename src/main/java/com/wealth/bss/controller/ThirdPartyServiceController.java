package com.wealth.bss.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.wealth.bss.contract.IfThirdPartyServiceContract;
import com.wealth.bss.serviceinterface.IfBrokerSyncService;
import com.wealth.bss.serviceinterface.IfThirdPartyService;
import com.wealth.common.generic.ApiResponse;
import com.wealth.common.generic.ApiResponseTemplate;
import com.wealth.common.generic.BaseError;
import com.wealth.common.generic.KarthiException;
import com.wealth.common.logger.KarthiLogger;
import com.wealth.common.model.dto.admin.ProviderRequestDto;
import com.wealth.common.model.dto.admin.ThirdPartyDto;
import com.wealth.common.model.dto.admin.ThirdPartySaveRequestDto;

@RestController
public class ThirdPartyServiceController implements IfThirdPartyServiceContract {

	private static final KarthiLogger LOGGER = new KarthiLogger(ThirdPartyServiceController.class);

	private final IfThirdPartyService thirdPartyService;
	private final IfBrokerSyncService brokerSyncService;
	private final ApiResponseTemplate responseTemplate;

	public ThirdPartyServiceController(IfThirdPartyService thirdPartyService, IfBrokerSyncService brokerSyncService, ApiResponseTemplate responseTemplate) {
		this.thirdPartyService = thirdPartyService;
		this.brokerSyncService = brokerSyncService;
		this.responseTemplate = responseTemplate;
	}

	@Override
	public ResponseEntity<ApiResponse<ThirdPartyDto, Void>> fetch(ProviderRequestDto request) throws KarthiException {
		LOGGER.info("Entering fetch");
		return responseTemplate.apires(brokerSyncService.fetchNow(request), BaseError.Success);
	}

	@Override
	public ResponseEntity<ApiResponse<List<ThirdPartyDto>, Void>> list() throws KarthiException {
		LOGGER.info("Entering list");
		return responseTemplate.apires(thirdPartyService.list(), BaseError.Success);
	}

	@Override
	public ResponseEntity<ApiResponse<ThirdPartyDto, Void>> save(ThirdPartySaveRequestDto request) throws KarthiException {
		LOGGER.info("Entering save");
		return responseTemplate.apires(thirdPartyService.save(request), BaseError.Success);
	}

	@Override
	public ResponseEntity<ApiResponse<ThirdPartyDto, Void>> test(ProviderRequestDto request) throws KarthiException {
		LOGGER.info("Entering test");
		return responseTemplate.apires(thirdPartyService.test(request), BaseError.Success);
	}

	@Override
	public ResponseEntity<ApiResponse<Boolean, Void>> remove(ProviderRequestDto request) throws KarthiException {
		LOGGER.info("Entering remove");
		thirdPartyService.remove(request);
		return responseTemplate.apires(Boolean.TRUE, BaseError.Success);
	}
}
