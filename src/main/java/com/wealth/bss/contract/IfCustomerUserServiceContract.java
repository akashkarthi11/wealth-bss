package com.wealth.bss.contract;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import com.wealth.bss.constants.WealthServiceConstants;
import com.wealth.common.generic.ApiResponse;
import com.wealth.common.generic.KarthiException;
import com.wealth.common.model.dto.customer.LoginRequestDto;
import com.wealth.common.model.dto.customer.LoginResponseDto;
import com.wealth.common.model.dto.customer.PasswordResetRequestDto;
import com.wealth.common.model.dto.customer.RegisterRequestDto;
import com.wealth.common.model.dto.customer.UserDto;

@RequestMapping(WealthServiceConstants.USERS_BASE)
public interface IfCustomerUserServiceContract {

	// Public: no token yet.
	@PostMapping(WealthServiceConstants.LOGIN)
	ResponseEntity<ApiResponse<LoginResponseDto, Void>> login(@RequestBody LoginRequestDto request)
			throws KarthiException;

	// Public: guarded by the owner's secret key.
	@PostMapping(WealthServiceConstants.REGISTER)
	ResponseEntity<ApiResponse<LoginResponseDto, Void>> register(@RequestBody RegisterRequestDto request)
			throws KarthiException;

	@PostMapping(WealthServiceConstants.ME)
	ResponseEntity<ApiResponse<UserDto, Void>> me() throws KarthiException;

	@PostMapping(WealthServiceConstants.LOGOUT)
	ResponseEntity<ApiResponse<Boolean, Void>> logout() throws KarthiException;

	// Admin only.
	@PostMapping(WealthServiceConstants.PASSWORD_RESET)
	ResponseEntity<ApiResponse<Boolean, Void>> resetPassword(@RequestBody PasswordResetRequestDto request)
			throws KarthiException;
}
