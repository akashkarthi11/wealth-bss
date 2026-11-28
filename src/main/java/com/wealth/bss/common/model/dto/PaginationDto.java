package com.wealth.bss.common.model.dto;

import java.io.Serializable;

import lombok.Data;

// Single argument object for DaoImple calls; ids are Strings because Wealth ids are UUIDs.
// userId is the caller (from the token context, never from a request body); id is the row asked for.
@Data
public class PaginationDto implements Serializable {

	private static final long serialVersionUID = 1L;

	private String userId;
	private String id;
	private String searchText;
	private int pageNo;
	private int pageSize;
	private String sortBy;
	// a list's filters and the keyset position of its last row (empty = none): kind, account, days (YYYY-MM-DD), and the day, creation time and id of the row to continue after
	private String kind;
	private String category;
	private String accountId;
	private String fromDay;
	private String toDay;
	private String cursorDay;
	private String cursorCreated;
	private String cursorId;
}
