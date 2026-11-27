package com.wealth.bss.contract;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import com.wealth.bss.constants.WealthServiceConstants;
import com.wealth.common.generic.ApiResponse;
import com.wealth.common.generic.KarthiException;
import com.wealth.common.model.dto.admin.ConnectionDto;
import com.wealth.common.model.dto.admin.ConnectionRequestDto;

// Admin only. Not public: the interceptor needs a valid token, and the service checks the role.
@RequestMapping(WealthServiceConstants.ENVIRONMENT_BASE)
public interface IfEnvironmentServiceContract {

	@PostMapping(WealthServiceConstants.ENV_REDIS_GET)
	ResponseEntity<ApiResponse<ConnectionDto, Void>> redisGet() throws KarthiException;

	@PostMapping(WealthServiceConstants.ENV_REDIS_UPDATE)
	ResponseEntity<ApiResponse<ConnectionDto, Void>> redisUpdate(@RequestBody ConnectionRequestDto request) throws KarthiException;

	@PostMapping(WealthServiceConstants.ENV_REDIS_RESET)
	ResponseEntity<ApiResponse<ConnectionDto, Void>> redisReset() throws KarthiException;

	@PostMapping(WealthServiceConstants.ENV_REDIS_ACTIVE)
	ResponseEntity<ApiResponse<ConnectionDto, Void>> redisActive(@RequestBody ConnectionRequestDto request) throws KarthiException;

	@PostMapping(WealthServiceConstants.ENV_KAFKA_ACTIVE)
	ResponseEntity<ApiResponse<ConnectionDto, Void>> kafkaActive(@RequestBody ConnectionRequestDto request) throws KarthiException;

	@PostMapping(WealthServiceConstants.ENV_KAFKA_REFRESH)
	ResponseEntity<ApiResponse<ConnectionDto, Void>> kafkaRefreshTime(@RequestBody ConnectionRequestDto request) throws KarthiException;

	@PostMapping(WealthServiceConstants.ENV_KAFKA_GET)
	ResponseEntity<ApiResponse<ConnectionDto, Void>> kafkaGet() throws KarthiException;

	@PostMapping(WealthServiceConstants.ENV_KAFKA_UPDATE)
	ResponseEntity<ApiResponse<ConnectionDto, Void>> kafkaUpdate(@RequestBody ConnectionRequestDto request) throws KarthiException;

	@PostMapping(WealthServiceConstants.ENV_KAFKA_RESET)
	ResponseEntity<ApiResponse<ConnectionDto, Void>> kafkaReset() throws KarthiException;

	@PostMapping(WealthServiceConstants.ENV_POSTGRES_GET)
	ResponseEntity<ApiResponse<ConnectionDto, Void>> postgresGet() throws KarthiException;

	@PostMapping(WealthServiceConstants.ENV_POSTGRES_UPDATE)
	ResponseEntity<ApiResponse<ConnectionDto, Void>> postgresUpdate(@RequestBody ConnectionRequestDto request) throws KarthiException;

	@PostMapping(WealthServiceConstants.ENV_POSTGRES_RESET)
	ResponseEntity<ApiResponse<ConnectionDto, Void>> postgresReset() throws KarthiException;

	@PostMapping(WealthServiceConstants.ENV_ADDRESS_SAVE)
	ResponseEntity<ApiResponse<ConnectionDto, Void>> addressSave(@RequestBody ConnectionRequestDto request) throws KarthiException;

	@PostMapping(WealthServiceConstants.ENV_SIGNUP_GET)
	ResponseEntity<ApiResponse<ConnectionDto, Void>> signupGet() throws KarthiException;

	@PostMapping(WealthServiceConstants.ENV_SIGNUP_UPDATE)
	ResponseEntity<ApiResponse<ConnectionDto, Void>> signupUpdate(@RequestBody ConnectionRequestDto request) throws KarthiException;

	@PostMapping(WealthServiceConstants.ENV_SIGNUP_CLOSE)
	ResponseEntity<ApiResponse<ConnectionDto, Void>> signupClose() throws KarthiException;

	@PostMapping(WealthServiceConstants.ENV_SIGNUP_RESET)
	ResponseEntity<ApiResponse<ConnectionDto, Void>> signupReset() throws KarthiException;
}
