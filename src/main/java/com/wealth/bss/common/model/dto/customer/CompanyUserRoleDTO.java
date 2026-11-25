package com.wealth.bss.common.model.dto.customer;

import java.io.Serializable;
import java.sql.Timestamp;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Transient;
import lombok.Data;

@Data
public class CompanyUserRoleDTO  implements Serializable {
	
	@Transient
	private static final long serialVersionUID = -8136255667519355474L;
	
	private int wkfId;
	private int compUserRoleId;
    private int userId;
    private int compRoleId;
    private int groupSmeCompId;
    private int smeCompId;
    private int bankId;
    private String userRoleDesc;
    @Schema(description = "User role status: A (Active) / I (Inactive)", example = "A")
	private char recordState;
	private Timestamp crtdDt;
	private String crtdIp;
	private int crtdBy;
	private Timestamp updtDt;
	private String updtIp;
	private int updtBy;
	private int menuId;
    private int permissionId;
    private String recordStatusCode;
  private String recordStatusCodeDesc;
  private String remarks;
	private String vcifId;

}