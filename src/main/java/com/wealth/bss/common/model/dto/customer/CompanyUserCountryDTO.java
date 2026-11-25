package com.wealth.bss.common.model.dto.customer;

import java.io.Serializable;
import java.sql.Timestamp;

import jakarta.persistence.Transient;
import lombok.Data;

@Data
public class CompanyUserCountryDTO implements Serializable {

	@Transient
	private static final long serialVersionUID = -6978238107191362788L;
	
	private int wkfId;
	private int userId;
	private int groupSmeCompId;
	private int smeCompId;
	private int smeCountryId;
	private int bankId;
	private String countryCode2;
	private String countryCode;
	private String countryName;
	private String currencyCode;
	private char recordState;
    private String recordStatusCode;
    private String recordStatusCodeDesc;
	private Timestamp crtdDt;
	private String crtdIp;
	private int crtdBy;
	private Timestamp updtDt;
	private String updtIp;
	private int updtBy;
	private char checkBox;
	private String vcifId;

}
