package com.wealth.bss.common.model.entity;

import java.sql.Timestamp;

import lombok.Data;
import lombok.EqualsAndHashCode;

// The figures read from one statement (table wealth_statement_figures). figures is one JSON object of label to value, whatever labels the bank uses.
@Data
@EqualsAndHashCode(callSuper = false)
public class WealthStatementFigureEntity extends GenericEntity {

	private static final long serialVersionUID = 1L;

	private String id;
	private String userId;
	private String mailRef;
	private String source;
	private String figures;
	private Timestamp readAt;
	// where it came from (gmail or upload), its state (opened, locked or failed), when it was first seen, and the PDF's file name
	private String origin;
	private String status;
	private Timestamp receivedAt;
	private String fileName;
	// The statement's transaction rows as a JSON array (kept until the user accepts them), how many there are, and when they were accepted.
	private String lines;
	private int rowCount;
	private Timestamp acceptedAt;

	@Override
	public String tableName() {
		return "WEALTH_STATEMENT_FIGURES";
	}

	@Override
	public String primaryKey() {
		return "id";
	}
}
