package com.wealth.bss.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.wealth.bss.contract.IfAccountsServiceContract;
import com.wealth.bss.serviceinterface.IfAccountsService;
import com.wealth.common.generic.ApiResponse;
import com.wealth.common.generic.ApiResponseTemplate;
import com.wealth.common.generic.BaseError;
import com.wealth.common.generic.KarthiException;
import com.wealth.common.logger.KarthiLogger;
import com.wealth.common.model.dto.AccountIdRequestDto;
import com.wealth.common.model.dto.IdRequestDto;
import com.wealth.common.model.dto.account.AccountDetailDto;
import com.wealth.common.model.dto.account.AccountSplitDto;
import com.wealth.common.model.dto.account.AccountSubtypeDto;
import com.wealth.common.model.dto.account.AccountsOverviewDto;

@RestController
public class AccountsServiceController implements IfAccountsServiceContract {

	private static final KarthiLogger LOGGER = new KarthiLogger(AccountsServiceController.class);

	private final IfAccountsService accountsService;
	private final ApiResponseTemplate responseTemplate;

	public AccountsServiceController(IfAccountsService accountsService, ApiResponseTemplate responseTemplate) {
		this.accountsService = accountsService;
		this.responseTemplate = responseTemplate;
	}

	@Override
	public ResponseEntity<ApiResponse<AccountsOverviewDto, Void>> list() throws KarthiException {
		LOGGER.info("Entering list");
		return responseTemplate.apires(accountsService.overview(), BaseError.Success);
	}

	@Override
	public ResponseEntity<ApiResponse<AccountDetailDto, Void>> detail(IdRequestDto request) throws KarthiException {
		LOGGER.info("Entering detail");
		return responseTemplate.apires(accountsService.detail(request), BaseError.Success);
	}

	@Override
	public ResponseEntity<ApiResponse<List<AccountSubtypeDto>, Void>> subtypes() throws KarthiException {
		LOGGER.info("Entering subtypes");
		return responseTemplate.apires(accountsService.subtypes(), BaseError.Success);
	}

	@Override
	public ResponseEntity<ApiResponse<List<AccountSplitDto>, Void>> splitsList(AccountIdRequestDto request) throws KarthiException {
		LOGGER.info("Entering splitsList");
		return responseTemplate.apires(accountsService.splits(request), BaseError.Success);
	}
}
