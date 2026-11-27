package com.wealth.bss.contract;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.wealth.bss.constants.WealthServiceConstants;
import com.wealth.common.generic.ApiResponse;
import com.wealth.common.generic.KarthiException;
import com.wealth.common.model.dto.people.PeopleOverviewDto;

// Signed-in users. Not public: the interceptor needs a valid token and every query filters by the user in it.
@RequestMapping(WealthServiceConstants.PEOPLE_BASE)
public interface IfPeopleServiceContract {

	@PostMapping(WealthServiceConstants.PEOPLE_LIST)
	ResponseEntity<ApiResponse<PeopleOverviewDto, Void>> list() throws KarthiException;

	@PostMapping(WealthServiceConstants.PEOPLE_REFRESH)
	ResponseEntity<ApiResponse<PeopleOverviewDto, Void>> refresh() throws KarthiException;
}
