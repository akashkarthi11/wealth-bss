package com.wealth.bss.contract;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import com.wealth.bss.constants.WealthServiceConstants;
import com.wealth.common.generic.ApiResponse;
import com.wealth.common.generic.KarthiException;
import com.wealth.common.model.dto.AccountIdRequestDto;
import com.wealth.common.model.dto.IdRequestDto;
import com.wealth.common.model.dto.account.AccountDetailDto;
import com.wealth.common.model.dto.account.AccountSplitDto;
import com.wealth.common.model.dto.account.AccountSubtypeDto;
import com.wealth.common.model.dto.account.AccountsOverviewDto;

// Signed-in users. Not public: the interceptor needs a valid token and every query filters by the user in it.
@RequestMapping(WealthServiceConstants.ACCOUNTS_BASE)
public interface IfAccountsServiceContract {

	@PostMapping(WealthServiceConstants.ACCOUNTS_LIST)
	ResponseEntity<ApiResponse<AccountsOverviewDto, Void>> list() throws KarthiException;

	@PostMapping(WealthServiceConstants.ACCOUNTS_DETAIL)
	ResponseEntity<ApiResponse<AccountDetailDto, Void>> detail(@RequestBody IdRequestDto request) throws KarthiException;

	@PostMapping(WealthServiceConstants.ACCOUNTS_SUBTYPES)
	ResponseEntity<ApiResponse<List<AccountSubtypeDto>, Void>> subtypes() throws KarthiException;

	@PostMapping(WealthServiceConstants.ACCOUNTS_SPLITS_LIST)
	ResponseEntity<ApiResponse<List<AccountSplitDto>, Void>> splitsList(@RequestBody AccountIdRequestDto request) throws KarthiException;
}
