package com.wealth.bss.controller;

import java.util.List;

import com.wealth.common.model.dto.admin.StatementHistoryDto;
import com.wealth.common.model.dto.admin.StatementDownloadRequestDto;
import com.wealth.common.model.dto.admin.StatementFileDto;
import com.wealth.common.model.dto.admin.StatementRetryRequestDto;
import com.wealth.common.model.dto.admin.StatementUploadRequestDto;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.wealth.bss.contract.IfGmailServiceContract;
import com.wealth.bss.serviceinterface.IfGmailService;
import com.wealth.common.generic.ApiResponse;
import com.wealth.common.generic.ApiResponseTemplate;
import com.wealth.common.generic.BaseError;
import com.wealth.common.generic.KarthiException;
import com.wealth.common.logger.KarthiLogger;
import com.wealth.common.model.dto.admin.GmailAppSaveRequestDto;
import com.wealth.common.model.dto.admin.GmailConnectRequestDto;
import com.wealth.common.model.dto.admin.GmailConnectUrlDto;
import com.wealth.common.model.dto.admin.GmailFetchResultDto;
import com.wealth.common.model.dto.admin.GmailFinishRequestDto;
import com.wealth.common.model.dto.admin.GmailPendingItemDto;
import com.wealth.common.model.dto.admin.GmailReviewRequestDto;
import com.wealth.common.model.dto.admin.GmailStatementResultDto;
import com.wealth.common.model.dto.admin.GmailStatementRowDto;
import com.wealth.common.model.dto.admin.GmailStatementSettingsRequestDto;
import com.wealth.common.model.dto.admin.GmailStatusDto;

@RestController
public class GmailServiceController implements IfGmailServiceContract {

	private static final KarthiLogger LOGGER = new KarthiLogger(GmailServiceController.class);

	private final IfGmailService gmailService;
	private final ApiResponseTemplate responseTemplate;

	public GmailServiceController(IfGmailService gmailService, ApiResponseTemplate responseTemplate) {
		this.gmailService = gmailService;
		this.responseTemplate = responseTemplate;
	}

	@Override
	public ResponseEntity<ApiResponse<GmailStatusDto, Void>> status() throws KarthiException {
		LOGGER.info("Entering status");
		return responseTemplate.apires(gmailService.status(), BaseError.Success);
	}

	@Override
	public ResponseEntity<ApiResponse<GmailConnectUrlDto, Void>> connectUrl(GmailConnectRequestDto request) throws KarthiException {
		LOGGER.info("Entering connectUrl");
		return responseTemplate.apires(gmailService.connectUrl(request), BaseError.Success);
	}

	@Override
	public ResponseEntity<ApiResponse<GmailStatusDto, Void>> connectFinish(GmailFinishRequestDto request) throws KarthiException {
		LOGGER.info("Entering connectFinish");
		return responseTemplate.apires(gmailService.connectFinish(request), BaseError.Success);
	}

	@Override
	public ResponseEntity<ApiResponse<GmailStatusDto, Void>> test() throws KarthiException {
		LOGGER.info("Entering test");
		return responseTemplate.apires(gmailService.test(), BaseError.Success);
	}

	@Override
	public ResponseEntity<ApiResponse<Boolean, Void>> disconnect() throws KarthiException {
		LOGGER.info("Entering disconnect");
		return responseTemplate.apires(gmailService.disconnect(), BaseError.Success);
	}

	@Override
	public ResponseEntity<ApiResponse<GmailStatusDto, Void>> statementSave(GmailStatementSettingsRequestDto request) throws KarthiException {
		LOGGER.info("Entering statementSave");
		return responseTemplate.apires(gmailService.statementSave(request), BaseError.Success);
	}

	@Override
	public ResponseEntity<ApiResponse<GmailStatementSettingsRequestDto, Void>> statementReveal() throws KarthiException {
		LOGGER.info("Entering statementReveal");
		return responseTemplate.apires(gmailService.statementReveal(), BaseError.Success);
	}

	@Override
	public ResponseEntity<ApiResponse<GmailStatusDto, Void>> statementClear() throws KarthiException {
		LOGGER.info("Entering statementClear");
		return responseTemplate.apires(gmailService.statementClear(), BaseError.Success);
	}

	@Override
	public ResponseEntity<ApiResponse<GmailStatementResultDto, Void>> statements() throws KarthiException {
		LOGGER.info("Entering statements");
		return responseTemplate.apires(gmailService.statements(), BaseError.Success);
	}

	@Override
	public ResponseEntity<ApiResponse<List<StatementHistoryDto>, Void>> statementHistory() throws KarthiException {
		LOGGER.info("Entering statementHistory");
		return responseTemplate.apires(gmailService.statementHistory(), BaseError.Success);
	}

	@Override
	public ResponseEntity<ApiResponse<GmailStatementResultDto, Void>> statementUpload(StatementUploadRequestDto request) throws KarthiException {
		LOGGER.info("Entering statementUpload");
		return responseTemplate.apires(gmailService.statementUpload(request), BaseError.Success);
	}

	@Override
	public ResponseEntity<ApiResponse<StatementFileDto, Void>> statementDownload(StatementDownloadRequestDto request) throws KarthiException {
		LOGGER.info("Entering statementDownload");
		return responseTemplate.apires(gmailService.statementDownload(request), BaseError.Success);
	}

	@Override
	public ResponseEntity<ApiResponse<GmailStatementResultDto, Void>> statementRetry(StatementRetryRequestDto request) throws KarthiException {
		LOGGER.info("Entering statementRetry");
		return responseTemplate.apires(gmailService.statementRetry(request), BaseError.Success);
	}

	@Override
	public ResponseEntity<ApiResponse<com.wealth.common.model.dto.transaction.BankImportResultDto, Void>> statementAccept(com.wealth.common.model.dto.admin.StatementAcceptRequestDto request)
			throws KarthiException {
		LOGGER.info("Entering statementAccept");
		return responseTemplate.apires(gmailService.statementAccept(request), BaseError.Success);
	}

	@Override
	public ResponseEntity<ApiResponse<List<GmailStatementRowDto>, Void>> statementList() throws KarthiException {
		LOGGER.info("Entering statementList");
		return responseTemplate.apires(gmailService.statementList(), BaseError.Success);
	}

	@Override
	public ResponseEntity<ApiResponse<GmailFetchResultDto, Void>> fetch() throws KarthiException {
		LOGGER.info("Entering fetch");
		return responseTemplate.apires(gmailService.fetch(), BaseError.Success);
	}

	@Override
	public ResponseEntity<ApiResponse<List<GmailPendingItemDto>, Void>> pending() throws KarthiException {
		LOGGER.info("Entering pending");
		return responseTemplate.apires(gmailService.pending(), BaseError.Success);
	}

	@Override
	public ResponseEntity<ApiResponse<Boolean, Void>> review(GmailReviewRequestDto request) throws KarthiException {
		LOGGER.info("Entering review");
		return responseTemplate.apires(gmailService.review(request), BaseError.Success);
	}

	@Override
	public ResponseEntity<ApiResponse<GmailStatusDto, Void>> appSave(GmailAppSaveRequestDto request) throws KarthiException {
		LOGGER.info("Entering appSave");
		return responseTemplate.apires(gmailService.appSave(request), BaseError.Success);
	}

	@Override
	public ResponseEntity<ApiResponse<Boolean, Void>> appRemove() throws KarthiException {
		LOGGER.info("Entering appRemove");
		return responseTemplate.apires(gmailService.appRemove(), BaseError.Success);
	}
}
