package com.wealth.bss.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.wealth.bss.contract.IfPeopleServiceContract;
import com.wealth.bss.serviceinterface.IfPeopleService;
import com.wealth.common.generic.ApiResponse;
import com.wealth.common.generic.ApiResponseTemplate;
import com.wealth.common.generic.BaseError;
import com.wealth.common.generic.KarthiException;
import com.wealth.common.logger.KarthiLogger;
import com.wealth.common.model.dto.IdRequestDto;
import com.wealth.common.model.dto.people.BillSplitDto;
import com.wealth.common.model.dto.people.PeopleOverviewDto;
import com.wealth.common.model.dto.people.SplitCreateRequestDto;

@RestController
public class PeopleServiceController implements IfPeopleServiceContract {

	private static final KarthiLogger LOGGER = new KarthiLogger(PeopleServiceController.class);

	private final IfPeopleService peopleService;
	private final ApiResponseTemplate responseTemplate;

	public PeopleServiceController(IfPeopleService peopleService, ApiResponseTemplate responseTemplate) {
		this.peopleService = peopleService;
		this.responseTemplate = responseTemplate;
	}

	@Override
	public ResponseEntity<ApiResponse<PeopleOverviewDto, Void>> list() throws KarthiException {
		LOGGER.info("Entering list");
		return responseTemplate.apires(peopleService.list(), BaseError.Success);
	}

	@Override
	public ResponseEntity<ApiResponse<PeopleOverviewDto, Void>> refresh() throws KarthiException {
		LOGGER.info("Entering refresh");
		return responseTemplate.apires(peopleService.refresh(), BaseError.Success);
	}

	@Override
	public ResponseEntity<ApiResponse<BillSplitDto, Void>> createSplit(SplitCreateRequestDto request) throws KarthiException {
		LOGGER.info("Entering createSplit");
		return responseTemplate.apires(peopleService.createSplit(request), BaseError.Success);
	}

	@Override
	public ResponseEntity<ApiResponse<Boolean, Void>> cancelSplit(IdRequestDto request) throws KarthiException {
		LOGGER.info("Entering cancelSplit");
		return responseTemplate.apires(peopleService.cancelSplit(request), BaseError.Success);
	}
}
