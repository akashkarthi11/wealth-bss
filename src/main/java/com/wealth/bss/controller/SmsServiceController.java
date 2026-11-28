package com.wealth.bss.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.wealth.bss.contract.IfSmsServiceContract;
import com.wealth.bss.serviceinterface.IfSmsService;
import com.wealth.common.generic.ApiResponse;
import com.wealth.common.generic.ApiResponseTemplate;
import com.wealth.common.generic.BaseError;
import com.wealth.common.generic.KarthiException;
import com.wealth.common.logger.KarthiLogger;
import com.wealth.common.model.dto.IdRequestDto;
import com.wealth.common.model.dto.sms.SmsDeviceCreateRequestDto;
import com.wealth.common.model.dto.sms.SmsDeviceDto;
import com.wealth.common.model.dto.sms.SmsPushRequestDto;
import com.wealth.common.model.dto.sms.SmsPushResultDto;
import com.wealth.common.model.dto.sms.SmsTokenRequestDto;

@RestController
public class SmsServiceController implements IfSmsServiceContract {

	private static final KarthiLogger LOGGER = new KarthiLogger(SmsServiceController.class);

	private final IfSmsService smsService;
	private final ApiResponseTemplate responseTemplate;

	public SmsServiceController(IfSmsService smsService, ApiResponseTemplate responseTemplate) {
		this.smsService = smsService;
		this.responseTemplate = responseTemplate;
	}

	@Override
	public ResponseEntity<ApiResponse<SmsDeviceDto, Void>> createDevice(SmsDeviceCreateRequestDto request) throws KarthiException {
		LOGGER.info("Entering sms device create");
		return responseTemplate.apires(smsService.createDevice(request), BaseError.Success);
	}

	@Override
	public ResponseEntity<ApiResponse<List<SmsDeviceDto>, Void>> listDevices() throws KarthiException {
		LOGGER.info("Entering sms device list");
		return responseTemplate.apires(smsService.listDevices(), BaseError.Success);
	}

	@Override
	public ResponseEntity<ApiResponse<Boolean, Void>> revokeDevice(IdRequestDto request) throws KarthiException {
		LOGGER.info("Entering sms device revoke");
		return responseTemplate.apires(smsService.revokeDevice(request), BaseError.Success);
	}

	@Override
	public ResponseEntity<ApiResponse<SmsPushResultDto, Void>> push(SmsPushRequestDto request) throws KarthiException {
		LOGGER.info("Entering sms push");
		return responseTemplate.apires(smsService.push(request), BaseError.Success);
	}

	@Override
	public ResponseEntity<ApiResponse<List<String>, Void>> banks(SmsTokenRequestDto request) throws KarthiException {
		LOGGER.info("Entering sms banks");
		return responseTemplate.apires(smsService.banks(request), BaseError.Success);
	}
}
