package com.wealth.bss.common.model.dto.customer;


import java.io.Serializable;
import java.sql.Timestamp;
import java.util.List;

import jakarta.persistence.Transient;
import lombok.Data;

@Data
public class CompanyUserBankDTO  implements Serializable {

	@Transient
	private static final long serialVersionUID = -7810311619019418655L;
	
	private int wkfId;
	private int userBankId;
	private int compBankId;
	private int bankId;
    private String bankName;
    private String bankCode;
    private String bankColourCode;
    private String logoName;
    private String logoPath;
    private String routingTypeCode;
    private String countryCode2;
	private int userId;
	private int smeCompId;
	private int smeCountryId;
	private char recordState;
	private String recordStatusCode;
	private String recordStatusCodeDesc;
	private Timestamp crtdDt;
//	private String crtdIp;
	private int crtdBy;
	private Timestamp updtDt;
//	private String updtIp;
	private int updtBy;
	private char checkBox;
	private char existRemoval;
	private int menuId;
    private int permissionId;
	private List<CompanyUserBankFeatureDTO>  userBankFeatureList;
	private String vcifId;

}
