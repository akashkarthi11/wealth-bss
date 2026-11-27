package com.wealth.bss.contract;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import com.wealth.bss.constants.WealthServiceConstants;
import com.wealth.common.generic.ApiResponse;
import com.wealth.common.generic.KarthiException;
import com.wealth.common.model.dto.admin.BrokerUserSaveRequestDto;
import com.wealth.common.model.dto.admin.BrokerUserStatusDto;

// The signed-in user's own Angel One connection. Not admin only: every call is for the caller's own login.
@RequestMapping(WealthServiceConstants.BROKER_BASE)
public interface IfBrokerUserServiceContract {

	@PostMapping(WealthServiceConstants.BROKER_STATUS)
	ResponseEntity<ApiResponse<BrokerUserStatusDto, Void>> status() throws KarthiException;

	@PostMapping(WealthServiceConstants.BROKER_SAVE)
	ResponseEntity<ApiResponse<BrokerUserStatusDto, Void>> save(@RequestBody BrokerUserSaveRequestDto request) throws KarthiException;

	@PostMapping(WealthServiceConstants.BROKER_TEST)
	ResponseEntity<ApiResponse<BrokerUserStatusDto, Void>> test() throws KarthiException;

	@PostMapping(WealthServiceConstants.BROKER_FETCH)
	ResponseEntity<ApiResponse<BrokerUserStatusDto, Void>> fetch() throws KarthiException;

	@PostMapping(WealthServiceConstants.BROKER_REMOVE)
	ResponseEntity<ApiResponse<Boolean, Void>> remove() throws KarthiException;
}
