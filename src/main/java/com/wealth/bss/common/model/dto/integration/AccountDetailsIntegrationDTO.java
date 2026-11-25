package com.wealth.bss.common.model.dto.integration;

import java.io.Serializable;

import jakarta.persistence.Transient;
import lombok.Data;

@Data
public class AccountDetailsIntegrationDTO implements Serializable {

	@Transient
	private static final long serialVersionUID = 5412331284807324674L;

	private String authorization;
	private int bankId;
	private int subFeatureId;
	private String scope;
	private String routingType;
	private String countryCode2;
	private String consentId;
	private String baseUrl;
	
	private String serviceCode;
	private String bankCode;
	private String countryCode;
	private String productCode;
	private String customerId;

	/*** Request **/
	private String cbsAccountId;
	private String accountNo;
	private String accountNumber;
	private String currencyCode;
	private int smeCompId;
	private int groupSmeCompId;
	private String tenantId;
	private String portalCompanyId;

	/*** Response **/
	private String accountCountryCode2;
	private String accountName;
	private String currentBalance;
	private String availableBalance;
	private String runningBalance;
	private String closingBalance;
	private String openingBalance;
	private String accountIbanNo;
	private String accountType;
	private String accountSubType;
	private String accountCurrencyCode;
	private String accountBankName;
	private String accountBiccode;
	private String accountStatus;
	private String accountOpenedDate;
	private String statusUpdatedDateTime;
	private char debitAllowed;
	private char creditAllowed;
	private char dormantAccount;
	private char initiatePaymentAllowed;
	private String accountOpeningDate;
	private String cifId;
	private String cifName;
	private String cifCountryCode2;
	private String accountCategory;
	private String accountHolderName;
	private String accountHolderShortName;
	private String accountTypeDescription;
	private String accountNickname;
	private String accountMaturityDate;
	private String accountSchemeNameIBAN;
	private String accountSchemeNameIBANName;
	private String accountSchemeNameAccountNumber;
	private String accountSchemeNameAccountNumberName;
	private String accountSchemeNameMaskedPAN;
	private String accountSchemeNameMaskedPANName;
	private String accountSchemeNameMortgageReference;
	private String accountSchemeNameMortgageReferenceName;
	private String accountSchemeNamePersonalLoanReference;
	private String accountSchemeNamePersonalLoanReferenceName;
	private String accountSchemeNameFinanceReference;
	private String accountSchemeNameFinanceReferenceName;
	
	private String accountBankSwiftBicCode;

	private String balanceType;
	private String dateTime;
	
	private String balanceClosingAvailableAmount;
	private String balanceClosingAvailableCurrency;

	private String balanceClosingBookedAmount;
	private String balanceClosingBookedCurrency;

	private String balanceClosingClearedAmount;
	private String balanceClosingClearedCurrency;

	private String balanceExpectedAmount;
	private String balanceExpectedCurrency;

	private String balanceForwardAvailableAmount;
	private String balanceForwardAvailableCurrency;

	private String balanceInformationAmount;
	private String balanceInformationCurrency;

	private String balanceInterimAvailableAmount; // available balance
	private String balanceInterimAvailableCurrency;

	private String balanceInterimBookedAmount;
	private String balanceInterimBookedCurrency;

	private String balanceInterimClearedAmount; // Current balance
	private String balanceInterimClearedCurrency;

	private String balanceOpeningAvailableAmount;
	private String balanceOpeningAvailableCurrency;

	private String balanceOpeningBookedAmount;
	private String balanceOpeningBookedCurrency;

	private String balanceOpeningClearedAmount;
	private String balanceOpeningClearedCurrency;

	private String balancePreviouslyClosedBookedAmount;
	private String balancePreviouslyClosedBookedCurrency;
	
	private String creditDebitIndicator;

	// CreditLine (Overdraft)
	private String creditLineType;
	private boolean creditLineIncluded;
		
	private String creditLineAvailableAmount;
	private String creditLineAvailableCurrency;

	private String creditLineCreditAmount;
	private String creditLineCreditCurrency;

	private String creditLineEmergencyAmount;
	private String creditLineEmergencyCurrency;

	private String creditLinePreAgreedAmount;
	private String creditLinePreAgreedCurrency;

	private String creditLineTemporaryAmount;
	private String creditLineTemporaryCurrency;

	private String overdraftLimit;
	private String overdraftUtilization;

	//Standards v2.1 -> New fields in Balance API
	private String principalAmount;
	private String principalCurrency;

	private String interestAmount;
	private String interestCurrency;

	private String feesAndChargesAmount;
	private String feesAndChargesCurrency;

	private String pastDueAmount;
	private String pastDueCurrency;

	private String profitAmount;
	private String profitCurrency;

	private String rentalAmount;
	private String rentalCurrency;

	
	/*** Error Status **/
	private String integrationStatusCode;
    private String integrationStatusDesc;
    private String rejectReasonCode;
    private String rejectReasonMessage;

    
    
    private String sanctionedAmount;
    private String disbursedAmount;
    private String payOffAmount;
    private String tenureMonths;
    private String loanNumber;
    private String bankReferenceNumber;
    private String loanStartDate;
    private String loanMaturityDate;
    
    private String interestPaid;
    private String nextPaymentDate;
    private String repaymentFrequency;
    private String emiAmount;
    private String interestRate;
    private String paymentDate;
    private char overdueFlag;
    
    private String cardNumber;
    private String creditLimit;
    private String expiryDate;
    private String cardName;
	private int menuId;
    private int permissionId;
    private int userId;
    private int compBankId;
    private String accountAvailBalance;
}
