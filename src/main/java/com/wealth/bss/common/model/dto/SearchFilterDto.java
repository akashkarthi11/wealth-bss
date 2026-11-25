package com.wealth.bss.common.model.dto;

import java.io.Serializable;
import java.sql.Timestamp;

import lombok.Data;

@Data
public class SearchFilterDto   implements Serializable {

private static final long serialVersionUID = 4162177486277192352L;

public String filterKey;
public String filterStringValue;
public int filterIntValue;
public String operator; 
public Timestamp startDate;
public Timestamp endDate;




}
