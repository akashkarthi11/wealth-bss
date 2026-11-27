package com.wealth.bss.common.model.entity;

import java.math.BigDecimal;

import lombok.Data;
import lombok.EqualsAndHashCode;

// A statement line in doubt, waiting for the user (table wealth_transaction_workflow). candidateIds are the ids of the transactions it may copy, separated by commas.
@Data
@EqualsAndHashCode(callSuper = false)
public class WealthTransactionWorkflowEntity extends GenericEntity {

	private static final long serialVersionUID = 1L;

	private String id;
	private String userId;
	private String source;
	private String sourceRef;
	private String occurredOn;
	private String description;
	private BigDecimal amount;
	private String direction;
	private String accountId;
	private String accountHint;
	private String reason;
	private String candidateIds;
	private String status;
	private String decidedBy;

	@Override
	public String tableName() {
		return "WEALTH_TRANSACTION_WORKFLOW";
	}

	@Override
	public String primaryKey() {
		return "id";
	}
}
