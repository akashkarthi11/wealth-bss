package com.wealth.bss.common.client;
import java.util.List;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import com.wealth.bss.common.config.CustomerMtlsFeignClientCommonConfig;
import com.wealth.bss.common.config.MtlsFeignClientCommonConfig;
import com.wealth.common.generic.ApiResponse;
import com.wealth.common.generic.KarthiException;
import com.wealth.bss.common.model.dto.PaginationDto;
import com.wealth.bss.common.model.dto.UserPermissionDTO;
import com.wealth.bss.common.model.dto.customer.CompanyUserDTO;
import com.wealth.bss.common.model.dto.master.MasBankFeatureDTO;
import com.wealth.bss.common.model.entity.system.SysCountryEntity;



@FeignClient(name = "karthi.customers-service-common",url = "${karthi.customers-service.url}",configuration = MtlsFeignClientCommonConfig.class)

public interface CustomersServiceFeignClient {

    @PostMapping("/growx/api/v1/customers-service/url/permissions")
    ResponseEntity<ApiResponse<UserPermissionDTO, Void>> getPermissionListByUser(@RequestBody UserPermissionDTO request,
        @RequestHeader("scope") String scope,
        @RequestHeader("X-USER-CODE") String userCode
    );
   
	@PostMapping("/growx/api/v1/customers-service/system/country/by-code")
	ResponseEntity<ApiResponse<SysCountryEntity, Void>> getCountryDataByCountryCode2(@RequestBody PaginationDto dto,
	        @RequestHeader("scope") String scope,
	        @RequestHeader("X-USER-CODE") String userCode)
			throws KarthiException;
	
	@PostMapping("/growx/api/v1/customers-service/bank/features/routingtype")
	ResponseEntity<ApiResponse<MasBankFeatureDTO, Void>> getBankByBankIdAndSubFeatureId(
	        @RequestParam int bankId,
	        @RequestParam int subFeatureId,
	        @RequestHeader("scope") String scope,
	        @RequestHeader("X-USER-CODE") String userCode) throws KarthiException;
	
	  @PostMapping("/growx/api/v1/customers-service/user-details")
	    ResponseEntity<ApiResponse<CompanyUserDTO, Void>> getCompanyUserDetails(
	            @RequestBody CompanyUserDTO request,
	            @RequestHeader("scope") String scope,
	            @RequestHeader("X-USER-CODE") String userCode) throws KarthiException;

	@PostMapping("/growx/api/v1/customers-service/bank/tenant-id")
	ResponseEntity<ApiResponse<String, Void>> getTenantIdByBankId(
			@RequestParam int bankId,
            @RequestHeader("scope") String scope,
            @RequestHeader("X-USER-CODE") String userCode) throws KarthiException;

}

