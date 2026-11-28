package com.wealth.bss.contract;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import com.wealth.bss.constants.WealthServiceConstants;
import com.wealth.common.generic.ApiResponse;
import com.wealth.common.generic.KarthiException;

@RequestMapping(WealthServiceConstants.FINVU_AA_BASE)
public interface IfFinvuAaContract {
	@PostMapping(WealthServiceConstants.FINVU_AA_CONFIG)
	ResponseEntity<ApiResponse<Map<String, Object>, Void>> configuration() throws KarthiException;
	@PostMapping(WealthServiceConstants.FINVU_AA_CONSENT_REQUEST)
	ResponseEntity<ApiResponse<Map<String, Object>, Void>> requestConsent(@RequestBody Map<String, Object> request) throws KarthiException;
	@PostMapping(WealthServiceConstants.FINVU_AA_CONSENT_STATUS)
	ResponseEntity<ApiResponse<Map<String, Object>, Void>> consentStatus(@RequestBody Map<String, Object> request) throws KarthiException;
	@PostMapping(WealthServiceConstants.FINVU_AA_FI_REQUEST)
	ResponseEntity<ApiResponse<Map<String, Object>, Void>> requestFinancialInformation(@RequestBody Map<String, Object> request) throws KarthiException;
	@PostMapping(WealthServiceConstants.FINVU_AA_FI_STATUS)
	ResponseEntity<ApiResponse<Map<String, Object>, Void>> financialInformationStatus(@RequestBody Map<String, Object> request) throws KarthiException;
	@PostMapping(WealthServiceConstants.FINVU_AA_FI_FETCH)
	ResponseEntity<ApiResponse<Map<String, Object>, Void>> fetchFinancialInformation(@RequestBody Map<String, Object> request) throws KarthiException;
}
