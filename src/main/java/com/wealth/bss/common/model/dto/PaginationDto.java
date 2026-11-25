package com.wealth.bss.common.model.dto;

import java.io.Serializable;

import lombok.Data;

// Single argument object for DaoImple calls; id is a String because Wealth ids are UUIDs.
@Data
public class PaginationDto implements Serializable {

	private static final long serialVersionUID = 1L;

	private String id;
	private String searchText;
	private int pageNo;
	private int pageSize;
	private String sortBy;
}
