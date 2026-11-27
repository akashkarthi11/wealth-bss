package com.wealth.bss.serviceinterface;

import java.util.List;

import com.wealth.common.model.dto.transaction.WorkflowDecisionRequestDto;
import com.wealth.common.model.dto.transaction.WorkflowItemDto;

// The bank lines waiting for the signed-in user's decision (the Data quality screen).
public interface IfTransactionWorkflowService {

	// The user's open lines, newest first, each with the transactions it may be a copy of.
	List<WorkflowItemDto> list();

	// merge: it is the same payment as a candidate (nothing is added; refused when there is no candidate). accept: it is a real new transaction (added, accepted). dismiss: ignore it for good.
	// Only an open line can be decided, once. WORKFLOW_NOT_FOUND when it is not the user's or was decided; WORKFLOW_DECISION_INVALID for another decision or a merge with no candidate.
	Boolean decide(WorkflowDecisionRequestDto request);
}
