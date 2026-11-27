package com.wealth.bss.common.model.entity;

import java.sql.Timestamp;

import lombok.Data;
import lombok.EqualsAndHashCode;

// A remark on an entry or a share, from the person or the owner. (table wealth_lend_comments)
@Data
@EqualsAndHashCode(callSuper = false)
public class WealthLendCommentEntity extends GenericEntity {

	private static final long serialVersionUID = 1L;

	private String id;
	private String entryId;
	private String shareId;
	private String author;
	private String body;
	private Timestamp createdAt;

	@Override
	public String tableName() {
		return "WEALTH_LEND_COMMENTS";
	}

	@Override
	public String primaryKey() {
		return "id";
	}
}
