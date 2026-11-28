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
import com.wealth.common.model.dto.people.CommentCreateRequestDto;
import com.wealth.common.model.dto.people.CommentCreateResultDto;
import com.wealth.common.model.dto.people.EntryNoteRequestDto;
import com.wealth.common.model.dto.people.EntryNoteResultDto;
import com.wealth.common.model.dto.people.RequestDecideRequestDto;
import com.wealth.common.model.dto.people.RequestDecideResultDto;
import com.wealth.common.model.dto.people.PersonMergeRequestDto;
import com.wealth.common.model.dto.people.PersonMergeResultDto;
import com.wealth.common.model.dto.people.ShareUpdateRequestDto;
import com.wealth.common.model.dto.people.ShareUpdateResultDto;
import com.wealth.common.model.dto.people.PersonEntryRequestDto;
import com.wealth.common.model.dto.people.PersonEntryResultDto;
import com.wealth.common.model.dto.people.PersonSaveRequestDto;
import com.wealth.common.model.dto.people.PersonSaveResultDto;
import com.wealth.common.model.dto.people.RecordStatusRequestDto;
import com.wealth.common.model.dto.people.RecordStatusResultDto;
import com.wealth.common.model.dto.people.RoundOffRequestDto;
import com.wealth.common.model.dto.people.RoundOffResultDto;
import com.wealth.common.model.dto.people.SharePaidRequestDto;
import com.wealth.common.model.dto.people.SharePaidResultDto;
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

	@Override
	public ResponseEntity<ApiResponse<PersonEntryResultDto, Void>> createEntries(PersonEntryRequestDto request) throws KarthiException {
		LOGGER.info("Entering createEntries");
		return responseTemplate.apires(peopleService.createEntries(request), BaseError.Success);
	}

	@Override
	public ResponseEntity<ApiResponse<SharePaidResultDto, Void>> markSharePaid(SharePaidRequestDto request) throws KarthiException {
		LOGGER.info("Entering markSharePaid");
		return responseTemplate.apires(peopleService.markSharePaid(request), BaseError.Success);
	}

	@Override
	public ResponseEntity<ApiResponse<RoundOffResultDto, Void>> roundOff(RoundOffRequestDto request) throws KarthiException {
		LOGGER.info("Entering roundOff");
		return responseTemplate.apires(peopleService.roundOff(request), BaseError.Success);
	}

	@Override
	public ResponseEntity<ApiResponse<RecordStatusResultDto, Void>> updateRecord(RecordStatusRequestDto request) throws KarthiException {
		LOGGER.info("Entering updateRecord");
		return responseTemplate.apires(peopleService.updateRecord(request), BaseError.Success);
	}

	@Override
	public ResponseEntity<ApiResponse<PersonSaveResultDto, Void>> savePerson(PersonSaveRequestDto request) throws KarthiException {
		LOGGER.info("Entering savePerson");
		return responseTemplate.apires(peopleService.savePerson(request), BaseError.Success);
	}

	@Override
	public ResponseEntity<ApiResponse<ShareUpdateResultDto, Void>> updateShare(ShareUpdateRequestDto request) throws KarthiException {
		LOGGER.info("Entering updateShare");
		return responseTemplate.apires(peopleService.updateShare(request), BaseError.Success);
	}

	@Override
	public ResponseEntity<ApiResponse<PersonMergeResultDto, Void>> mergePeople(PersonMergeRequestDto request) throws KarthiException {
		LOGGER.info("Entering mergePeople");
		return responseTemplate.apires(peopleService.mergePeople(request), BaseError.Success);
	}

	@Override
	public ResponseEntity<ApiResponse<CommentCreateResultDto, Void>> createComment(CommentCreateRequestDto request) throws KarthiException {
		LOGGER.info("Entering createComment");
		return responseTemplate.apires(peopleService.createComment(request), BaseError.Success);
	}

	@Override
	public ResponseEntity<ApiResponse<EntryNoteResultDto, Void>> updateEntryNote(EntryNoteRequestDto request) throws KarthiException {
		LOGGER.info("Entering updateEntryNote");
		return responseTemplate.apires(peopleService.updateEntryNote(request), BaseError.Success);
	}

	@Override
	public ResponseEntity<ApiResponse<RequestDecideResultDto, Void>> decideRequest(RequestDecideRequestDto request) throws KarthiException {
		LOGGER.info("Entering decideRequest");
		return responseTemplate.apires(peopleService.decideRequest(request), BaseError.Success);
	}
}
