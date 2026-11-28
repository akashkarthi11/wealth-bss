package com.wealth.bss.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.wealth.bss.contract.IfCustomerUserServiceContract;
import com.wealth.bss.serviceinterface.IfCustomerUserService;
import com.wealth.common.generic.ApiResponse;
import com.wealth.common.generic.ApiResponseTemplate;
import com.wealth.common.generic.BaseError;
import com.wealth.common.generic.KarthiException;
import com.wealth.common.logger.KarthiLogger;
import com.wealth.common.model.dto.customer.LoginRequestDto;
import com.wealth.common.model.dto.customer.LoginResponseDto;
import com.wealth.common.model.dto.customer.PasswordChangeRequestDto;
import com.wealth.common.model.dto.customer.ProfileSaveRequestDto;
import com.wealth.common.model.dto.customer.PasswordResetRequestDto;
import com.wealth.common.model.dto.customer.RegisterRequestDto;
import com.wealth.common.model.dto.customer.UserDto;

@RestController
public class CustomerUserServiceController implements IfCustomerUserServiceContract {

	private static final KarthiLogger LOGGER = new KarthiLogger(CustomerUserServiceController.class);

	private final IfCustomerUserService customerUserService;
	private final ApiResponseTemplate responseTemplate;

	public CustomerUserServiceController(IfCustomerUserService customerUserService,
			ApiResponseTemplate responseTemplate) {
		this.customerUserService = customerUserService;
		this.responseTemplate = responseTemplate;
	}

	@Override
	public ResponseEntity<ApiResponse<LoginResponseDto, Void>> login(LoginRequestDto request) throws KarthiException {
		LOGGER.info("Entering login");
		if (request == null) {
			throw new KarthiException(BaseError.INVALID_REQUEST);
		}
		return responseTemplate.apires(customerUserService.login(request), BaseError.Success);
	}

	@Override
	public ResponseEntity<ApiResponse<LoginResponseDto, Void>> register(RegisterRequestDto request)
			throws KarthiException {
		LOGGER.info("Entering register");
		if (request == null) {
			throw new KarthiException(BaseError.INVALID_REQUEST);
		}
		return responseTemplate.apires(customerUserService.register(request), BaseError.Success);
	}

	@Override
	public ResponseEntity<ApiResponse<UserDto, Void>> me() throws KarthiException {
		LOGGER.info("Entering me");
		return responseTemplate.apires(customerUserService.me(), BaseError.Success);
	}

	@Override
	public ResponseEntity<ApiResponse<Boolean, Void>> logout() throws KarthiException {
		LOGGER.info("Entering logout");
		customerUserService.logout();
		return responseTemplate.apires(Boolean.TRUE, BaseError.Success);
	}

	@Override
	public ResponseEntity<ApiResponse<Boolean, Void>> resetPassword(PasswordResetRequestDto request)
			throws KarthiException {
		LOGGER.info("Entering resetPassword");
		if (request == null) {
			throw new KarthiException(BaseError.INVALID_REQUEST);
		}
		customerUserService.resetPassword(request);
		return responseTemplate.apires(Boolean.TRUE, BaseError.Success);
	}

	@Override
	public ResponseEntity<ApiResponse<UserDto, Void>> profileSave(ProfileSaveRequestDto request) throws KarthiException {
		LOGGER.info("Entering profileSave");
		return responseTemplate.apires(customerUserService.profileSave(request), BaseError.Success);
	}

	@Override
	public ResponseEntity<ApiResponse<Boolean, Void>> changePassword(PasswordChangeRequestDto request) throws KarthiException {
		LOGGER.info("Entering changePassword");
		customerUserService.changePassword(request);
		return responseTemplate.apires(Boolean.TRUE, BaseError.Success);
	}
}
