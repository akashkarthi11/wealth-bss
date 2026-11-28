package com.wealth.bss.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.wealth.bss.contract.IfFinvuAaContract;
import com.wealth.bss.serviceinterface.IfFinvuAaService;
import com.wealth.common.generic.ApiResponse;
import com.wealth.common.generic.ApiResponseTemplate;
import com.wealth.common.generic.BaseError;
import com.wealth.common.generic.KarthiException;

@RestController
public class FinvuAaController implements IfFinvuAaContract {
	private final IfFinvuAaService service;
	private final ApiResponseTemplate responseTemplate;

	public FinvuAaController(IfFinvuAaService service, ApiResponseTemplate responseTemplate) {
		this.service = service;
		this.responseTemplate = responseTemplate;
	}

	@Override public ResponseEntity<ApiResponse<Map<String, Object>, Void>> configuration() throws KarthiException {
		return responseTemplate.apires(service.configuration(), BaseError.Success);
	}
	@Override public ResponseEntity<ApiResponse<Map<String, Object>, Void>> requestConsent(Map<String, Object> request) throws KarthiException {
		return responseTemplate.apires(service.requestConsent(request), BaseError.Success);
	}
	@Override public ResponseEntity<ApiResponse<Map<String, Object>, Void>> consentStatus(Map<String, Object> request) throws KarthiException {
		return responseTemplate.apires(service.consentStatus(request), BaseError.Success);
	}
	@Override public ResponseEntity<ApiResponse<Map<String, Object>, Void>> requestFinancialInformation(Map<String, Object> request) throws KarthiException {
		return responseTemplate.apires(service.requestFinancialInformation(request), BaseError.Success);
	}
	@Override public ResponseEntity<ApiResponse<Map<String, Object>, Void>> financialInformationStatus(Map<String, Object> request) throws KarthiException {
		return responseTemplate.apires(service.financialInformationStatus(request), BaseError.Success);
	}
	@Override public ResponseEntity<ApiResponse<Map<String, Object>, Void>> fetchFinancialInformation(Map<String, Object> request) throws KarthiException {
		return responseTemplate.apires(service.fetchFinancialInformation(request), BaseError.Success);
	}
}
