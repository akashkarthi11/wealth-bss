package com.wealth.bss.serviceinterface;

import java.util.List;

import com.wealth.common.model.dto.admin.StatementHistoryDto;
import com.wealth.common.model.dto.admin.StatementDownloadRequestDto;
import com.wealth.common.model.dto.admin.StatementFileDto;
import com.wealth.common.model.dto.admin.StatementRetryRequestDto;
import com.wealth.common.model.dto.admin.StatementUploadRequestDto;

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

// Each user connects their own Gmail (read-only) so bank alert emails can be read. The Google client is one for everyone (admin);
// the mailbox token is the user's own. Tokens and the client secret are kept encrypted and never returned.
public interface IfGmailService {

	// The caller's connection and whether the Google client is set up.
	GmailStatusDto status();

	// The Google consent page for the caller. GMAIL_NOT_CONFIGURED until an admin has saved the Google client.
	GmailConnectUrlDto connectUrl(GmailConnectRequestDto request);

	// Finishes the consent: checks the state belongs to the caller, swaps the code for tokens and saves the connection.
	GmailStatusDto connectFinish(GmailFinishRequestDto request);

	// Checks the saved connection with Google and saves the result (ok or failed).
	GmailStatusDto test();

	// Takes the grant back at Google (best effort) and removes the caller's connection.
	boolean disconnect();

	// Saves the PAN and date of birth that statement PDFs are locked with, encrypted with the Gmail token. A blank value keeps the saved one.
	GmailStatusDto statementSave(GmailStatementSettingsRequestDto request);

	// The caller's own saved PAN, date of birth and name letters, for the screen to show on request (masked until the user taps reveal). Only the caller's own, only on this call: the status
	// never carries them, and they are never logged. A value that is not saved is empty. GMAIL_NOT_CONNECTED when there is no Gmail connection.
	GmailStatementSettingsRequestDto statementReveal();

	// Removes the saved PAN and date of birth.
	GmailStatusDto statementClear();

	// Opens the caller's recent statement PDFs from Gmail in memory and returns the labelled figures read from them. Nothing is saved and no PDF is kept.
	GmailStatementResultDto statements();

	// The statements the caller has read, most recent first, each with its figures as label to value (the saved JSON column split into pairs).
	List<GmailStatementRowDto> statementList();

	// Every statement the user received from Gmail or uploaded, opened, locked or failed, newest first. A locked one is a row with no figures.
	List<StatementHistoryDto> statementHistory();

	// A statement PDF the user chose: opened in memory with the saved passwords and the one typed for it, never stored. Opened: its figures and rows are kept and imported like a Gmail statement.
	// Locked: only a history row. GMAIL_REQUEST_INVALID for a file that is not a PDF, empty, over 5 MB or with a name or password too long.
	GmailStatementResultDto statementUpload(StatementUploadRequestDto request);

	// A locked Gmail statement opened again with a password typed for it (the PDF is fetched from Gmail again). keep saves that password with the others, only when it opens the file.
	// GMAIL_REQUEST_INVALID when the statement is not the user's, not locked, or was uploaded (an uploaded file is not kept: upload it again).
	GmailStatementResultDto statementRetry(StatementRetryRequestDto request);

	// The PDF of one of the user's Gmail statements (opened or locked), fetched from Gmail when asked and never stored. GMAIL_REQUEST_INVALID when the statement is not the user's or was uploaded
	// (an uploaded file is not kept); GMAIL_FETCH_FAILED when Gmail no longer has the mail or the file.
	StatementFileDto statementDownload(StatementDownloadRequestDto request);

	// The user accepts a statement: each of its rows is checked against the database by the import (a new one becomes a transaction, one already there is skipped, one with a problem goes to the
	// Data quality review as its own item), in date and time order, and the statement is marked accepted. Accepting again repeats nothing. GMAIL_REQUEST_INVALID when the statement is not the user's or did not open.
	com.wealth.common.model.dto.transaction.BankImportResultDto statementAccept(com.wealth.common.model.dto.admin.StatementAcceptRequestDto request);

	// Reads the caller's recent bank alert mails (HDFC, slice) and saves each transaction once for review (review_status pending, never changing a balance).
	GmailFetchResultDto fetch();

	// The caller's Gmail transactions that wait for a decision.
	List<GmailPendingItemDto> pending();

	// Accepts or dismisses one pending Gmail transaction of the caller.
	boolean review(GmailReviewRequestDto request);

	// Admin: saves the Google client id and secret (a blank secret keeps the saved one) and turns the service on.
	GmailStatusDto appSave(GmailAppSaveRequestDto request);

	// Admin: removes the Google client and turns the service off.
	boolean appRemove();
}
