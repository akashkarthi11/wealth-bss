package com.wealth.bss.contract;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import com.wealth.bss.constants.WealthServiceConstants;
import com.wealth.common.generic.ApiResponse;
import com.wealth.common.generic.KarthiException;
import com.wealth.common.model.dto.IdRequestDto;
import com.wealth.common.model.dto.transaction.TransactionCreateRequestDto;
import com.wealth.common.model.dto.transaction.TransactionDto;
import com.wealth.common.model.dto.transaction.TransactionPageDto;
import com.wealth.common.model.dto.transaction.TransactionPageRequestDto;
import com.wealth.common.model.dto.transaction.WorkflowDecisionRequestDto;
import com.wealth.common.model.dto.transaction.WorkflowItemDto;
import java.util.List;

// Signed-in users. Not public: the interceptor needs a valid token and every query filters by the user in it.
@RequestMapping(WealthServiceConstants.TRANSACTIONS_BASE)
public interface IfTransactionsServiceContract {

	@PostMapping(WealthServiceConstants.TRANSACTIONS_LIST)
	ResponseEntity<ApiResponse<TransactionPageDto, Void>> list(@RequestBody(required = false) TransactionPageRequestDto request) throws KarthiException;

	@PostMapping(WealthServiceConstants.TRANSACTIONS_SUMMARY)
	ResponseEntity<ApiResponse<com.wealth.common.model.dto.transaction.TransactionSummaryDto, Void>> summary(@RequestBody(required = false) TransactionPageRequestDto request) throws KarthiException;

	@PostMapping(WealthServiceConstants.TRANSACTIONS_REFRESH)
	ResponseEntity<ApiResponse<TransactionPageDto, Void>> refresh() throws KarthiException;

	@PostMapping(WealthServiceConstants.TRANSACTIONS_CREATE)
	ResponseEntity<ApiResponse<TransactionDto, Void>> create(@RequestBody TransactionCreateRequestDto request) throws KarthiException;

	@PostMapping(WealthServiceConstants.TRANSACTIONS_WORKFLOW_LIST)
	ResponseEntity<ApiResponse<List<WorkflowItemDto>, Void>> workflowList() throws KarthiException;

	@PostMapping(WealthServiceConstants.TRANSACTIONS_WORKFLOW_DECIDE)
	ResponseEntity<ApiResponse<Boolean, Void>> workflowDecide(@RequestBody WorkflowDecisionRequestDto request) throws KarthiException;

	@PostMapping(WealthServiceConstants.TRANSACTIONS_DELETE)
	ResponseEntity<ApiResponse<Boolean, Void>> delete(@RequestBody IdRequestDto request) throws KarthiException;
}
