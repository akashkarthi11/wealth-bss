package com.wealth.bss.common.model.dto.customer;

import java.io.Serializable;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Transient;
import lombok.Data;

@Data
public class CompanyUserFeatureAccDTO implements Serializable {

	@Transient
	private static final long serialVersionUID = -4759355784248436787L;

	private int wkfId;
	private int subFeatureId;
	private int userFeatureAccId;
	private int compBankId;
	private int userId;
	private int accountId;
	private String portalCompanyId;
	public String accountName;
	public String accountNo;
	private String accountNickname;
	private String accountType;
	private String accountSubType;
	private String accountBiccode;
	private String mobileCode;
	private String mobileNo;
	private String emailId;
	private int denomination;
	private String accountIbanNo;
	private BigDecimal accountAvailBalance;
	private BigDecimal accountCurrBalance;
	private Timestamp accountOpenedDate;
	private String accountCurrencyCode;
	private String accountCountryCode2;
	private Timestamp accessStartDate;
	private Timestamp accessEndDate;
	private String accountBankName;
	private String accountHolderName;
	private String accountTypeDescription;

	@Schema(description = "Transaction Approval Limit: Y/N", example = "Y")
	private char isTransApprovalLimit;

	@Schema(description = "Daily Transaction Limit: Y/N", example = "Y")
	private char isDailyTransLimit;
	private BigDecimal transApprovalLimitAmount;
	private BigDecimal dailyTransLimitAmount;

	@Schema(description = "Show Account balance: Y/N", example = "Y")
	private char showAccBalance;

	@Schema(description = "Show Statement: Y/N", example = "Y")
	private char showStatements;
	private char recordState;
	private Timestamp crtdDt;
	private String crtdIp;
	private int crtdBy;
	private Timestamp updtDt;
	private String updtIp;
	private int updtBy;
	private char checkBox;

	private int bankId;
	private String bankName;
	private String bankCode;
	private String bankColourCode;
	private String logoName;
	private String logoPath;
	private String addressLine1;
	private String addressLine2;
	private String addressLine3;
	private String addressLine4;

	private int menuId;
	private int permissionId;
	private int smeCompId;
	private String cbsAccountId;
	private String routingTypeCode;
	private String consentId;
	private String consentType;
	private String consentUsage;
	private String consentUsageSubtype;
	private String vcifId;
	private BigDecimal thresholdAmount;
	private Timestamp consentEndDateTime;

	private List<CbsParties> cbsParties;

	@Data
	public static class CbsParties implements Serializable {

		@Transient
		private static final long serialVersionUID = -8829968696185125684L;
		private String partyType;
		private String partyCategory;
	}
}
