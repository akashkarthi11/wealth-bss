package com.wealth.bss.contract;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.wealth.bss.constants.WealthServiceConstants;
import com.wealth.common.generic.ApiResponse;
import com.wealth.common.generic.KarthiException;
import com.wealth.common.model.dto.invest.InvestmentsDto;

// Signed-in users. Not public: the interceptor needs a valid token and the query filters by the user in it.
@RequestMapping(WealthServiceConstants.INVESTMENTS_BASE)
public interface IfInvestmentsServiceContract {

	@PostMapping(WealthServiceConstants.INVESTMENTS_LIST)
	ResponseEntity<ApiResponse<InvestmentsDto, Void>> list() throws KarthiException;

	@PostMapping(WealthServiceConstants.INVESTMENTS_REFRESH)
	ResponseEntity<ApiResponse<InvestmentsDto, Void>> refresh() throws KarthiException;
}
