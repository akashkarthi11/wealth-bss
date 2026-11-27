package com.wealth.bss.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.wealth.bss.contract.IfTransactionsServiceContract;
import com.wealth.bss.serviceinterface.IfTransactionsService;
import com.wealth.common.generic.ApiResponse;
import com.wealth.common.generic.ApiResponseTemplate;
import com.wealth.common.generic.BaseError;
import com.wealth.common.generic.KarthiException;
import com.wealth.common.logger.KarthiLogger;
import com.wealth.common.model.dto.IdRequestDto;
import com.wealth.common.model.dto.transaction.TransactionCreateRequestDto;
import com.wealth.common.model.dto.transaction.TransactionDto;
import com.wealth.common.model.dto.transaction.TransactionPageDto;
import com.wealth.common.model.dto.transaction.TransactionPageRequestDto;
import com.wealth.common.model.dto.transaction.WorkflowDecisionRequestDto;
import com.wealth.common.model.dto.transaction.WorkflowItemDto;
import com.wealth.bss.serviceinterface.IfTransactionWorkflowService;
import java.util.List;

@RestController
public class TransactionsServiceController implements IfTransactionsServiceContract {

	private static final KarthiLogger LOGGER = new KarthiLogger(TransactionsServiceController.class);

	private final IfTransactionsService transactionsService;
	private final IfTransactionWorkflowService workflowService;
	private final ApiResponseTemplate responseTemplate;

	public TransactionsServiceController(IfTransactionsService transactionsService, IfTransactionWorkflowService workflowService, ApiResponseTemplate responseTemplate) {
		this.transactionsService = transactionsService;
		this.workflowService = workflowService;
		this.responseTemplate = responseTemplate;
	}

	@Override
	public ResponseEntity<ApiResponse<TransactionPageDto, Void>> list(TransactionPageRequestDto request) throws KarthiException {
		LOGGER.info("Entering list");
		return responseTemplate.apires(transactionsService.list(request), BaseError.Success);
	}

	@Override
	public ResponseEntity<ApiResponse<com.wealth.common.model.dto.transaction.TransactionSummaryDto, Void>> summary(TransactionPageRequestDto request) throws KarthiException {
		LOGGER.info("Entering summary");
		return responseTemplate.apires(transactionsService.summary(request), BaseError.Success);
	}

	@Override
	public ResponseEntity<ApiResponse<TransactionPageDto, Void>> refresh() throws KarthiException {
		LOGGER.info("Entering refresh");
		return responseTemplate.apires(transactionsService.refresh(), BaseError.Success);
	}

	@Override
	public ResponseEntity<ApiResponse<TransactionDto, Void>> create(TransactionCreateRequestDto request) throws KarthiException {
		LOGGER.info("Entering create");
		return responseTemplate.apires(transactionsService.create(request), BaseError.Success);
	}

	@Override
	public ResponseEntity<ApiResponse<List<WorkflowItemDto>, Void>> workflowList() throws KarthiException {
		LOGGER.info("Entering workflowList");
		return responseTemplate.apires(workflowService.list(), BaseError.Success);
	}

	@Override
	public ResponseEntity<ApiResponse<Boolean, Void>> workflowDecide(WorkflowDecisionRequestDto request) throws KarthiException {
		LOGGER.info("Entering workflowDecide");
		return responseTemplate.apires(workflowService.decide(request), BaseError.Success);
	}

	@Override
	public ResponseEntity<ApiResponse<Boolean, Void>> delete(IdRequestDto request) throws KarthiException {
		LOGGER.info("Entering delete");
		return responseTemplate.apires(transactionsService.delete(request), BaseError.Success);
	}
}
