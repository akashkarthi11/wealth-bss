package com.wealth.bss.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.wealth.bss.contract.IfBrokerUserServiceContract;
import com.wealth.bss.serviceinterface.IfBrokerSyncService;
import com.wealth.common.generic.ApiResponse;
import com.wealth.common.generic.ApiResponseTemplate;
import com.wealth.common.generic.BaseError;
import com.wealth.common.generic.KarthiException;
import com.wealth.common.logger.KarthiLogger;
import com.wealth.common.model.dto.admin.BrokerUserSaveRequestDto;
import com.wealth.common.model.dto.admin.BrokerUserStatusDto;

@RestController
public class BrokerUserServiceController implements IfBrokerUserServiceContract {

	private static final KarthiLogger LOGGER = new KarthiLogger(BrokerUserServiceController.class);

	private final IfBrokerSyncService brokerService;
	private final ApiResponseTemplate responseTemplate;

	public BrokerUserServiceController(IfBrokerSyncService brokerService, ApiResponseTemplate responseTemplate) {
		this.brokerService = brokerService;
		this.responseTemplate = responseTemplate;
	}

	@Override
	public ResponseEntity<ApiResponse<BrokerUserStatusDto, Void>> status() throws KarthiException {
		LOGGER.info("Entering broker status");
		return responseTemplate.apires(brokerService.userStatus(), BaseError.Success);
	}

	@Override
	public ResponseEntity<ApiResponse<BrokerUserStatusDto, Void>> save(BrokerUserSaveRequestDto request) throws KarthiException {
		LOGGER.info("Entering broker save");
		return responseTemplate.apires(brokerService.userSave(request), BaseError.Success);
	}

	@Override
	public ResponseEntity<ApiResponse<BrokerUserStatusDto, Void>> test() throws KarthiException {
		LOGGER.info("Entering broker test");
		return responseTemplate.apires(brokerService.userTest(), BaseError.Success);
	}

	@Override
	public ResponseEntity<ApiResponse<BrokerUserStatusDto, Void>> fetch() throws KarthiException {
		LOGGER.info("Entering broker fetch");
		return responseTemplate.apires(brokerService.userFetch(), BaseError.Success);
	}

	@Override
	public ResponseEntity<ApiResponse<Boolean, Void>> remove() throws KarthiException {
		LOGGER.info("Entering broker remove");
		return responseTemplate.apires(brokerService.userRemove(), BaseError.Success);
	}
}
