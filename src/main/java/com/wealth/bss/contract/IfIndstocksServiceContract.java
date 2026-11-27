package com.wealth.bss.contract;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import com.wealth.bss.constants.WealthServiceConstants;
import com.wealth.common.generic.ApiResponse;
import com.wealth.common.generic.KarthiException;
import com.wealth.common.model.dto.admin.IndstocksSaveRequestDto;
import com.wealth.common.model.dto.admin.IndstocksStatusDto;

// The signed-in user's own INDstocks connection. Not admin only: every call is for the caller's own token.
@RequestMapping(WealthServiceConstants.INDSTOCKS_BASE)
public interface IfIndstocksServiceContract {

	@PostMapping(WealthServiceConstants.BROKER_STATUS)
	ResponseEntity<ApiResponse<IndstocksStatusDto, Void>> status() throws KarthiException;

	@PostMapping(WealthServiceConstants.BROKER_SAVE)
	ResponseEntity<ApiResponse<IndstocksStatusDto, Void>> save(@RequestBody IndstocksSaveRequestDto request) throws KarthiException;

	@PostMapping(WealthServiceConstants.BROKER_TEST)
	ResponseEntity<ApiResponse<IndstocksStatusDto, Void>> test() throws KarthiException;

	@PostMapping(WealthServiceConstants.BROKER_REMOVE)
	ResponseEntity<ApiResponse<Boolean, Void>> remove() throws KarthiException;
}
