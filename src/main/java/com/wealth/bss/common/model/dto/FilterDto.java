package com.wealth.bss.common.model.dto;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Data;

@Data
public class FilterDto  implements Serializable {

	private static final long serialVersionUID = -4971490571876444997L;
	
	public String filterKey;
	public String filterValue;

	

//	public int getFilterIntValue() {
//		return Integer.valueOf(filterValue).intValue();
//	}
//
//	public long getFilterLongValue() {
//		return Integer.valueOf(filterValue).longValue();
//	}

	@JsonIgnore // <--- Add this
    public int getFilterIntValue() {
        if (filterValue == null || !filterValue.matches("-?\\d+")) {
            return 0; // Or throw a custom business exception
        }
        return Integer.parseInt(filterValue);
    }

    @JsonIgnore // <--- Add this
    public long getFilterLongValue() {
        if (filterValue == null || !filterValue.matches("-?\\d+")) {
            return 0L;
        }
        return Long.parseLong(filterValue);
    }
	
}
