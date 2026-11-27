package com.wealth.bss.contract;

import java.util.List;

import com.wealth.common.model.dto.admin.StatementHistoryDto;
import com.wealth.common.model.dto.admin.StatementDownloadRequestDto;
import com.wealth.common.model.dto.admin.StatementFileDto;
import com.wealth.common.model.dto.admin.StatementRetryRequestDto;
import com.wealth.common.model.dto.admin.StatementUploadRequestDto;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import com.wealth.bss.constants.WealthServiceConstants;
import com.wealth.common.generic.ApiResponse;
import com.wealth.common.generic.KarthiException;
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

// Not public: the interceptor needs a valid token. Status, connect, test and disconnect act on the caller's own connection; app-save and app-remove are admin only.
@RequestMapping(WealthServiceConstants.GMAIL_BASE)
public interface IfGmailServiceContract {

	@PostMapping(WealthServiceConstants.GMAIL_STATUS)
	ResponseEntity<ApiResponse<GmailStatusDto, Void>> status() throws KarthiException;

	@PostMapping(WealthServiceConstants.GMAIL_CONNECT_URL)
	ResponseEntity<ApiResponse<GmailConnectUrlDto, Void>> connectUrl(@RequestBody GmailConnectRequestDto request) throws KarthiException;

	@PostMapping(WealthServiceConstants.GMAIL_CONNECT_FINISH)
	ResponseEntity<ApiResponse<GmailStatusDto, Void>> connectFinish(@RequestBody GmailFinishRequestDto request) throws KarthiException;

	@PostMapping(WealthServiceConstants.GMAIL_TEST)
	ResponseEntity<ApiResponse<GmailStatusDto, Void>> test() throws KarthiException;

	@PostMapping(WealthServiceConstants.GMAIL_DISCONNECT)
	ResponseEntity<ApiResponse<Boolean, Void>> disconnect() throws KarthiException;

	@PostMapping(WealthServiceConstants.GMAIL_STATEMENT_SAVE)
	ResponseEntity<ApiResponse<GmailStatusDto, Void>> statementSave(@RequestBody GmailStatementSettingsRequestDto request) throws KarthiException;

	@PostMapping(WealthServiceConstants.GMAIL_STATEMENT_REVEAL)
	ResponseEntity<ApiResponse<GmailStatementSettingsRequestDto, Void>> statementReveal() throws KarthiException;

	@PostMapping(WealthServiceConstants.GMAIL_STATEMENT_CLEAR)
	ResponseEntity<ApiResponse<GmailStatusDto, Void>> statementClear() throws KarthiException;

	@PostMapping(WealthServiceConstants.GMAIL_STATEMENTS)
	ResponseEntity<ApiResponse<GmailStatementResultDto, Void>> statements() throws KarthiException;

	@PostMapping(WealthServiceConstants.GMAIL_STATEMENT_HISTORY)
	ResponseEntity<ApiResponse<List<StatementHistoryDto>, Void>> statementHistory() throws KarthiException;

	@PostMapping(WealthServiceConstants.GMAIL_STATEMENT_UPLOAD)
	ResponseEntity<ApiResponse<GmailStatementResultDto, Void>> statementUpload(@RequestBody StatementUploadRequestDto request) throws KarthiException;

	@PostMapping(WealthServiceConstants.GMAIL_STATEMENT_DOWNLOAD)
	ResponseEntity<ApiResponse<StatementFileDto, Void>> statementDownload(@RequestBody StatementDownloadRequestDto request) throws KarthiException;

	@PostMapping(WealthServiceConstants.GMAIL_STATEMENT_RETRY)
	ResponseEntity<ApiResponse<GmailStatementResultDto, Void>> statementRetry(@RequestBody StatementRetryRequestDto request) throws KarthiException;

	@PostMapping(WealthServiceConstants.GMAIL_STATEMENT_ACCEPT)
	ResponseEntity<ApiResponse<com.wealth.common.model.dto.transaction.BankImportResultDto, Void>> statementAccept(@RequestBody com.wealth.common.model.dto.admin.StatementAcceptRequestDto request)
			throws KarthiException;

	@PostMapping(WealthServiceConstants.GMAIL_STATEMENT_LIST)
	ResponseEntity<ApiResponse<List<GmailStatementRowDto>, Void>> statementList() throws KarthiException;

	@PostMapping(WealthServiceConstants.GMAIL_FETCH)
	ResponseEntity<ApiResponse<GmailFetchResultDto, Void>> fetch() throws KarthiException;

	@PostMapping(WealthServiceConstants.GMAIL_PENDING)
	ResponseEntity<ApiResponse<List<GmailPendingItemDto>, Void>> pending() throws KarthiException;

	@PostMapping(WealthServiceConstants.GMAIL_REVIEW)
	ResponseEntity<ApiResponse<Boolean, Void>> review(@RequestBody GmailReviewRequestDto request) throws KarthiException;

	@PostMapping(WealthServiceConstants.GMAIL_APP_SAVE)
	ResponseEntity<ApiResponse<GmailStatusDto, Void>> appSave(@RequestBody GmailAppSaveRequestDto request) throws KarthiException;

	@PostMapping(WealthServiceConstants.GMAIL_APP_REMOVE)
	ResponseEntity<ApiResponse<Boolean, Void>> appRemove() throws KarthiException;
}
