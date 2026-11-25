package com.wealth.bss.common.model.dto.customer;

import java.io.Serializable;
import java.sql.Timestamp;
import java.util.List;

import jakarta.persistence.Transient;
import lombok.Data;

@Data
public class CompanyUserBankFeatureDTO implements Serializable { 

	@Transient
	private static final long serialVersionUID = -4619737211024957295L;
	
	private int wkfId;
	private int userBankFeatureId;
    private int compBankId;
    private int bankId;
    private String bankName;
    private int subFeatureId;
    private String subFeatureName;
    private int featureId;
    private String featureName;
    private int activityId;
    private String activityName;
    private int smeCompId;
    private int userId;
    private int smeCountryId;
    private int feeId;
	private Timestamp crtdDt;
//	private String crtdIp;
	private int crtdBy;
	private Timestamp updtDt;
//	private String updtIp;
	private int updtBy;
	private char recordState;
	private char checkBox;
	private char existRemoval;
	private int menuId;
    private int permissionId;
	private List<CompanyUserFeatureAccDTO> featureAccountDto;
	private String vcifId;

}
