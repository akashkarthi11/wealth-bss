package com.wealth.bss.contract;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import com.wealth.bss.constants.WealthServiceConstants;
import com.wealth.common.generic.ApiResponse;
import com.wealth.common.generic.KarthiException;
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

// Signed-in users. Not public: the interceptor needs a valid token and every query filters by the user in it.
@RequestMapping(WealthServiceConstants.PEOPLE_BASE)
public interface IfPeopleServiceContract {

	@PostMapping(WealthServiceConstants.PEOPLE_LIST)
	ResponseEntity<ApiResponse<PeopleOverviewDto, Void>> list() throws KarthiException;

	@PostMapping(WealthServiceConstants.PEOPLE_REFRESH)
	ResponseEntity<ApiResponse<PeopleOverviewDto, Void>> refresh() throws KarthiException;

	@PostMapping(WealthServiceConstants.PEOPLE_SPLIT_CREATE)
	ResponseEntity<ApiResponse<BillSplitDto, Void>> createSplit(@RequestBody SplitCreateRequestDto request) throws KarthiException;

	@PostMapping(WealthServiceConstants.PEOPLE_SPLIT_CANCEL)
	ResponseEntity<ApiResponse<Boolean, Void>> cancelSplit(@RequestBody IdRequestDto request) throws KarthiException;

	@PostMapping(WealthServiceConstants.PEOPLE_ENTRY_CREATE)
	ResponseEntity<ApiResponse<PersonEntryResultDto, Void>> createEntries(@RequestBody PersonEntryRequestDto request) throws KarthiException;

	@PostMapping(WealthServiceConstants.PEOPLE_SHARE_PAID)
	ResponseEntity<ApiResponse<SharePaidResultDto, Void>> markSharePaid(@RequestBody SharePaidRequestDto request) throws KarthiException;

	@PostMapping(WealthServiceConstants.PEOPLE_ROUNDOFF)
	ResponseEntity<ApiResponse<RoundOffResultDto, Void>> roundOff(@RequestBody RoundOffRequestDto request) throws KarthiException;

	@PostMapping(WealthServiceConstants.PEOPLE_RECORDS_UPDATE)
	ResponseEntity<ApiResponse<RecordStatusResultDto, Void>> updateRecord(@RequestBody RecordStatusRequestDto request) throws KarthiException;

	@PostMapping(WealthServiceConstants.PEOPLE_SAVE)
	ResponseEntity<ApiResponse<PersonSaveResultDto, Void>> savePerson(@RequestBody PersonSaveRequestDto request) throws KarthiException;

	@PostMapping(WealthServiceConstants.PEOPLE_SHARE_UPDATE)
	ResponseEntity<ApiResponse<ShareUpdateResultDto, Void>> updateShare(@RequestBody ShareUpdateRequestDto request) throws KarthiException;

	@PostMapping(WealthServiceConstants.PEOPLE_MERGE)
	ResponseEntity<ApiResponse<PersonMergeResultDto, Void>> mergePeople(@RequestBody PersonMergeRequestDto request) throws KarthiException;

	@PostMapping(WealthServiceConstants.PEOPLE_COMMENT_CREATE)
	ResponseEntity<ApiResponse<CommentCreateResultDto, Void>> createComment(@RequestBody CommentCreateRequestDto request) throws KarthiException;

	@PostMapping(WealthServiceConstants.PEOPLE_ENTRY_NOTE)
	ResponseEntity<ApiResponse<EntryNoteResultDto, Void>> updateEntryNote(@RequestBody EntryNoteRequestDto request) throws KarthiException;

	@PostMapping(WealthServiceConstants.PEOPLE_REQUEST_DECIDE)
	ResponseEntity<ApiResponse<RequestDecideResultDto, Void>> decideRequest(@RequestBody RequestDecideRequestDto request) throws KarthiException;
}
