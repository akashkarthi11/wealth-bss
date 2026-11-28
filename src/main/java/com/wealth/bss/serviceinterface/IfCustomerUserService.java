package com.wealth.bss.serviceinterface;

import com.wealth.common.model.dto.customer.LoginRequestDto;
import com.wealth.common.model.dto.customer.LoginResponseDto;
import com.wealth.common.model.dto.customer.PasswordChangeRequestDto;
import com.wealth.common.model.dto.customer.ProfileSaveRequestDto;
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

	// The signed-in user saves their own profile details (a blank text clears a value). INVALID_REQUEST for a name over 60 characters, a mobile that is not 7 to 20 digits, plus, space or dash,
	// a date format other than short, numeric or iso, or a home screen that is not a menu key. Returns the user as saved.
	UserDto profileSave(ProfileSaveRequestDto request);

	// The signed-in user changes their own password: PASSWORD_MISMATCH when the current one is wrong (counted like a failed sign-in), the password policy for the new one. Every older token of the user,
	// this one included, stops working, so the client signs in again.
	void changePassword(PasswordChangeRequestDto request);

	// Admin only; signs out every older token of that user.
	void resetPassword(PasswordResetRequestDto request);
}
