package com.wealth.bss.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.wealth.bss.contract.IfLendViewServiceContract;
import com.wealth.bss.serviceinterface.IfPeopleService;
import com.wealth.common.generic.ApiResponse;
import com.wealth.common.generic.ApiResponseTemplate;
import com.wealth.common.generic.BaseError;
import com.wealth.common.generic.KarthiException;
import com.wealth.common.logger.KarthiLogger;
import com.wealth.common.model.dto.people.LendPaidRequestDto;
import com.wealth.common.model.dto.people.LendPaidResultDto;
import com.wealth.common.model.dto.people.LendRemarkRequestDto;
import com.wealth.common.model.dto.people.LendRemarkResultDto;
import com.wealth.common.model.dto.people.LendViewDto;
import com.wealth.common.model.dto.people.LendViewRequestDto;

@RestController
public class LendViewServiceController implements IfLendViewServiceContract {

	private static final KarthiLogger LOGGER = new KarthiLogger(LendViewServiceController.class);

	private final IfPeopleService peopleService;
	private final ApiResponseTemplate responseTemplate;

	public LendViewServiceController(IfPeopleService peopleService, ApiResponseTemplate responseTemplate) {
		this.peopleService = peopleService;
		this.responseTemplate = responseTemplate;
	}

	@Override
	public ResponseEntity<ApiResponse<LendViewDto, Void>> view(LendViewRequestDto request) throws KarthiException {
		LOGGER.info("Entering lend view");
		return responseTemplate.apires(peopleService.lendView(request), BaseError.Success);
	}

	@Override
	public ResponseEntity<ApiResponse<LendPaidResultDto, Void>> paid(LendPaidRequestDto request) throws KarthiException {
		LOGGER.info("Entering lend paid");
		return responseTemplate.apires(peopleService.lendPaid(request), BaseError.Success);
	}

	@Override
	public ResponseEntity<ApiResponse<LendRemarkResultDto, Void>> remark(LendRemarkRequestDto request) throws KarthiException {
		LOGGER.info("Entering lend remark");
		return responseTemplate.apires(peopleService.lendRemark(request), BaseError.Success);
	}
}
