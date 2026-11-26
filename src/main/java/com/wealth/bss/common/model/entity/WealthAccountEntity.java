package com.wealth.bss.common.model.entity;

import java.math.BigDecimal;
import java.util.Date;

import lombok.Data;
import lombok.EqualsAndHashCode;

// The columns account_type, current_balance, balance_as_of and balance_status are legacy copies and are not mapped:
// the type is subtypeCode and the balance comes from the account balance entity.
@Data
@EqualsAndHashCode(callSuper = false)
public class WealthAccountEntity extends GenericEntity {

	private static final long serialVersionUID = 1L;

	private String id;
	private String userId;
	private String accountKey;
	private String bankName;
	private String accountName;
	private String subtypeCode;
	private String maskedIdentifier;
	private String currency;
	private String status;
	private BigDecimal fdPrincipal;
	private Date fdBookedOn;
	private Date fdMaturityOn;
	private String fdPayout;
	private String fdCompounding;

	@Override
	public String tableName() {
		return "WEALTH_ACCOUNTS";
	}

	@Override
	public String primaryKey() {
		return "id";
	}
}
