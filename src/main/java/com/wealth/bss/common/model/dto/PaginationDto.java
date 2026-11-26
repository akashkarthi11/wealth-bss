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
}
