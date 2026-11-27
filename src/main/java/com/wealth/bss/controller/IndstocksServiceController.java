package com.wealth.bss.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.wealth.bss.contract.IfIndstocksServiceContract;
import com.wealth.bss.serviceinterface.IfIndstocksService;
import com.wealth.common.generic.ApiResponse;
import com.wealth.common.generic.ApiResponseTemplate;
import com.wealth.common.generic.BaseError;
import com.wealth.common.generic.KarthiException;
import com.wealth.common.logger.KarthiLogger;
import com.wealth.common.model.dto.admin.IndstocksSaveRequestDto;
import com.wealth.common.model.dto.admin.IndstocksStatusDto;

@RestController
public class IndstocksServiceController implements IfIndstocksServiceContract {

	private static final KarthiLogger LOGGER = new KarthiLogger(IndstocksServiceController.class);

	private final IfIndstocksService indstocksService;
	private final ApiResponseTemplate responseTemplate;

	public IndstocksServiceController(IfIndstocksService indstocksService, ApiResponseTemplate responseTemplate) {
		this.indstocksService = indstocksService;
		this.responseTemplate = responseTemplate;
	}

	@Override
	public ResponseEntity<ApiResponse<IndstocksStatusDto, Void>> status() throws KarthiException {
		LOGGER.info("Entering indstocks status");
		return responseTemplate.apires(indstocksService.status(), BaseError.Success);
	}

	@Override
	public ResponseEntity<ApiResponse<IndstocksStatusDto, Void>> save(IndstocksSaveRequestDto request) throws KarthiException {
		LOGGER.info("Entering indstocks save");
		return responseTemplate.apires(indstocksService.save(request), BaseError.Success);
	}

	@Override
	public ResponseEntity<ApiResponse<IndstocksStatusDto, Void>> test() throws KarthiException {
		LOGGER.info("Entering indstocks test");
		return responseTemplate.apires(indstocksService.test(), BaseError.Success);
	}

	@Override
	public ResponseEntity<ApiResponse<Boolean, Void>> remove() throws KarthiException {
		LOGGER.info("Entering indstocks remove");
		return responseTemplate.apires(indstocksService.remove(), BaseError.Success);
	}
}
