package com.wealth.bss.contract;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import com.wealth.bss.constants.WealthServiceConstants;
import com.wealth.common.generic.ApiResponse;
import com.wealth.common.generic.KarthiException;
import com.wealth.common.model.dto.admin.ProviderRequestDto;
import com.wealth.common.model.dto.admin.ThirdPartyDto;
import com.wealth.common.model.dto.admin.ThirdPartySaveRequestDto;

// Admin only. Not public: the interceptor needs a valid token, and the service checks the role.
@RequestMapping(WealthServiceConstants.THIRD_PARTY_BASE)
public interface IfThirdPartyServiceContract {

	@PostMapping(WealthServiceConstants.THIRD_PARTY_LIST)
	ResponseEntity<ApiResponse<List<ThirdPartyDto>, Void>> list() throws KarthiException;

	@PostMapping(WealthServiceConstants.THIRD_PARTY_SAVE)
	ResponseEntity<ApiResponse<ThirdPartyDto, Void>> save(@RequestBody ThirdPartySaveRequestDto request) throws KarthiException;

	@PostMapping(WealthServiceConstants.THIRD_PARTY_TEST)
	ResponseEntity<ApiResponse<ThirdPartyDto, Void>> test(@RequestBody ProviderRequestDto request) throws KarthiException;

	@PostMapping(WealthServiceConstants.THIRD_PARTY_FETCH)
	ResponseEntity<ApiResponse<ThirdPartyDto, Void>> fetch(@RequestBody ProviderRequestDto request) throws KarthiException;

	@PostMapping(WealthServiceConstants.THIRD_PARTY_REMOVE)
	ResponseEntity<ApiResponse<Boolean, Void>> remove(@RequestBody ProviderRequestDto request) throws KarthiException;
}
