package com.wealth.bss.serviceinterface;

import com.wealth.common.model.dto.customer.LoginRequestDto;
import com.wealth.common.model.dto.customer.LoginResponseDto;
import com.wealth.common.model.dto.customer.PasswordResetRequestDto;
import com.wealth.common.model.dto.customer.RegisterRequestDto;
import com.wealth.common.model.dto.customer.UserDto;

public interface IfCustomerUserService {

	LoginResponseDto login(LoginRequestDto request);

	LoginResponseDto register(RegisterRequestDto request);

	// The signed-in user, from the cached context and the database.
	UserDto me();

	// Drops the cached context; the client discards its token.
	void logout();

	// Admin only; signs out every older token of that user.
	void resetPassword(PasswordResetRequestDto request);
}
