package com.wealth.bss.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.wealth.bss.contract.IfEnvironmentServiceContract;
import com.wealth.bss.serviceinterface.IfEnvironmentService;
import com.wealth.common.generic.ApiResponse;
import com.wealth.common.generic.ApiResponseTemplate;
import com.wealth.common.generic.BaseError;
import com.wealth.common.generic.KarthiException;
import com.wealth.common.logger.KarthiLogger;
import com.wealth.common.model.dto.admin.ConnectionDto;
import com.wealth.common.model.dto.admin.ConnectionRequestDto;

@RestController
public class EnvironmentServiceController implements IfEnvironmentServiceContract {

	private static final KarthiLogger LOGGER = new KarthiLogger(EnvironmentServiceController.class);

	private final IfEnvironmentService environmentService;
	private final ApiResponseTemplate responseTemplate;

	public EnvironmentServiceController(IfEnvironmentService environmentService, ApiResponseTemplate responseTemplate) {
		this.environmentService = environmentService;
		this.responseTemplate = responseTemplate;
	}

	@Override
	public ResponseEntity<ApiResponse<ConnectionDto, Void>> redisGet() throws KarthiException {
		LOGGER.info("Entering redisGet");
		return responseTemplate.apires(environmentService.redisGet(), BaseError.Success);
	}

	@Override
	public ResponseEntity<ApiResponse<ConnectionDto, Void>> redisUpdate(ConnectionRequestDto request) throws KarthiException {
		LOGGER.info("Entering redisUpdate");
		return responseTemplate.apires(environmentService.redisUpdate(request), BaseError.Success);
	}

	@Override
	public ResponseEntity<ApiResponse<ConnectionDto, Void>> redisReset() throws KarthiException {
		LOGGER.info("Entering redisReset");
		return responseTemplate.apires(environmentService.redisReset(), BaseError.Success);
	}

	@Override
	public ResponseEntity<ApiResponse<ConnectionDto, Void>> redisActive(ConnectionRequestDto request) throws KarthiException {
		LOGGER.info("Entering redisActive");
		return responseTemplate.apires(environmentService.redisActive(request), BaseError.Success);
	}

	@Override
	public ResponseEntity<ApiResponse<ConnectionDto, Void>> kafkaRefreshTime(ConnectionRequestDto request) throws KarthiException {
		LOGGER.info("Entering kafkaRefreshTime");
		return responseTemplate.apires(environmentService.kafkaRefreshTime(request), BaseError.Success);
	}

	@Override
	public ResponseEntity<ApiResponse<ConnectionDto, Void>> kafkaActive(ConnectionRequestDto request) throws KarthiException {
		LOGGER.info("Entering kafkaActive");
		return responseTemplate.apires(environmentService.kafkaActive(request), BaseError.Success);
	}

	@Override
	public ResponseEntity<ApiResponse<ConnectionDto, Void>> kafkaGet() throws KarthiException {
		LOGGER.info("Entering kafkaGet");
		return responseTemplate.apires(environmentService.kafkaGet(), BaseError.Success);
	}

	@Override
	public ResponseEntity<ApiResponse<ConnectionDto, Void>> kafkaUpdate(ConnectionRequestDto request) throws KarthiException {
		LOGGER.info("Entering kafkaUpdate");
		return responseTemplate.apires(environmentService.kafkaUpdate(request), BaseError.Success);
	}

	@Override
	public ResponseEntity<ApiResponse<ConnectionDto, Void>> kafkaReset() throws KarthiException {
		LOGGER.info("Entering kafkaReset");
		return responseTemplate.apires(environmentService.kafkaReset(), BaseError.Success);
	}

	@Override
	public ResponseEntity<ApiResponse<ConnectionDto, Void>> postgresGet() throws KarthiException {
		LOGGER.info("Entering postgresGet");
		return responseTemplate.apires(environmentService.postgresGet(), BaseError.Success);
	}

	@Override
	public ResponseEntity<ApiResponse<ConnectionDto, Void>> postgresUpdate(ConnectionRequestDto request) throws KarthiException {
		LOGGER.info("Entering postgresUpdate");
		return responseTemplate.apires(environmentService.postgresUpdate(request), BaseError.Success);
	}

	@Override
	public ResponseEntity<ApiResponse<ConnectionDto, Void>> postgresReset() throws KarthiException {
		LOGGER.info("Entering postgresReset");
		return responseTemplate.apires(environmentService.postgresReset(), BaseError.Success);
	}

	@Override
	public ResponseEntity<ApiResponse<ConnectionDto, Void>> addressSave(ConnectionRequestDto request) throws KarthiException {
		LOGGER.info("Entering addressSave");
		return responseTemplate.apires(environmentService.addressSave(request), BaseError.Success);
	}
}
