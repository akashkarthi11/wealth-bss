package com.wealth.bss.contract;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import com.wealth.common.generic.ApiResponse;
import com.wealth.common.generic.KarthiException;
import com.wealth.common.model.dto.customer.LoginRequestDto;
import com.wealth.common.model.dto.customer.LoginResponseDto;
import com.wealth.common.model.dto.customer.PasswordResetRequestDto;
import com.wealth.common.model.dto.customer.RegisterRequestDto;
import com.wealth.common.model.dto.customer.UserDto;

@RequestMapping("/wealth-service/api/v1/customers/users")
public interface IfCustomerUserServiceContract {

	// Public: no token yet.
	@PostMapping("/login")
	ResponseEntity<ApiResponse<LoginResponseDto, Void>> login(@RequestBody LoginRequestDto request)
			throws KarthiException;

	// Public: guarded by the owner's secret key.
	@PostMapping("/register")
	ResponseEntity<ApiResponse<LoginResponseDto, Void>> register(@RequestBody RegisterRequestDto request)
			throws KarthiException;

	@PostMapping("/me")
	ResponseEntity<ApiResponse<UserDto, Void>> me() throws KarthiException;

	@PostMapping("/logout")
	ResponseEntity<ApiResponse<Boolean, Void>> logout() throws KarthiException;

	// Admin only.
	@PostMapping("/password/reset")
	ResponseEntity<ApiResponse<Boolean, Void>> resetPassword(@RequestBody PasswordResetRequestDto request)
			throws KarthiException;
}
