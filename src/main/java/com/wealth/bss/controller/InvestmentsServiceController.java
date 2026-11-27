package com.wealth.bss.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.wealth.bss.contract.IfInvestmentsServiceContract;
import com.wealth.bss.serviceinterface.IfInvestmentsService;
import com.wealth.common.generic.ApiResponse;
import com.wealth.common.generic.ApiResponseTemplate;
import com.wealth.common.generic.BaseError;
import com.wealth.common.generic.KarthiException;
import com.wealth.common.logger.KarthiLogger;
import com.wealth.common.model.dto.invest.HoldingDto;
import com.wealth.common.model.dto.invest.HoldingSaveRequestDto;
import com.wealth.common.model.dto.invest.InvestmentsDto;

@RestController
public class InvestmentsServiceController implements IfInvestmentsServiceContract {

	private static final KarthiLogger LOGGER = new KarthiLogger(InvestmentsServiceController.class);

	private final IfInvestmentsService investmentsService;
	private final ApiResponseTemplate responseTemplate;

	public InvestmentsServiceController(IfInvestmentsService investmentsService, ApiResponseTemplate responseTemplate) {
		this.investmentsService = investmentsService;
		this.responseTemplate = responseTemplate;
	}

	@Override
	public ResponseEntity<ApiResponse<InvestmentsDto, Void>> list() throws KarthiException {
		LOGGER.info("Entering list");
		return responseTemplate.apires(investmentsService.list(), BaseError.Success);
	}

	@Override
	public ResponseEntity<ApiResponse<HoldingDto, Void>> saveHolding(HoldingSaveRequestDto request) throws KarthiException {
		LOGGER.info("Entering saveHolding");
		return responseTemplate.apires(investmentsService.saveHolding(request), BaseError.Success);
	}

	@Override
	public ResponseEntity<ApiResponse<InvestmentsDto, Void>> refresh() throws KarthiException {
		LOGGER.info("Entering refresh");
		return responseTemplate.apires(investmentsService.refresh(), BaseError.Success);
	}
}
