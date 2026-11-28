package com.wealth.bss.contract;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.wealth.bss.constants.WealthServiceConstants;
import com.wealth.common.generic.ApiResponse;
import com.wealth.common.generic.KarthiException;
import com.wealth.common.model.dto.IdRequestDto;
import com.wealth.common.model.dto.sms.SmsDeviceCreateRequestDto;
import com.wealth.common.model.dto.sms.SmsDeviceDto;
import com.wealth.common.model.dto.sms.SmsPushRequestDto;
import com.wealth.common.model.dto.sms.SmsPushResultDto;
import com.wealth.common.model.dto.sms.SmsTokenRequestDto;

// The device calls need a signed-in user. The push is public (the interceptor lets the /sms-ingest prefix through): the phone's secret token travels in the body and is checked first.
public interface IfSmsServiceContract {

	@PostMapping(WealthServiceConstants.SMS_DEVICE_CREATE)
	ResponseEntity<ApiResponse<SmsDeviceDto, Void>> createDevice(@RequestBody SmsDeviceCreateRequestDto request) throws KarthiException;

	@PostMapping(WealthServiceConstants.SMS_DEVICE_LIST)
	ResponseEntity<ApiResponse<List<SmsDeviceDto>, Void>> listDevices() throws KarthiException;

	@PostMapping(WealthServiceConstants.SMS_DEVICE_REVOKE)
	ResponseEntity<ApiResponse<Boolean, Void>> revokeDevice(@RequestBody IdRequestDto request) throws KarthiException;

	@PostMapping(WealthServiceConstants.SMS_PUSH)
	ResponseEntity<ApiResponse<SmsPushResultDto, Void>> push(@RequestBody SmsPushRequestDto request) throws KarthiException;

	@PostMapping(WealthServiceConstants.SMS_BANKS)
	ResponseEntity<ApiResponse<List<String>, Void>> banks(@RequestBody SmsTokenRequestDto request) throws KarthiException;
}
