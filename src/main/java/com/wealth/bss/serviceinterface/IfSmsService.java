package com.wealth.bss.serviceinterface;

import java.util.List;

import com.wealth.common.model.dto.IdRequestDto;
import com.wealth.common.model.dto.sms.SmsDeviceCreateRequestDto;
import com.wealth.common.model.dto.sms.SmsDeviceDto;
import com.wealth.common.model.dto.sms.SmsPushRequestDto;
import com.wealth.common.model.dto.sms.SmsPushResultDto;

// Reading bank SMS like Gmail: a user links a phone (the phone app gets a secret token) and the phone sends each bank SMS, which is read into a transaction by the same import as a Gmail alert.
public interface IfSmsService {

	// Links a phone for the signed-in user. The token is in the answer once and only its hash is kept. SMS_LABEL_INVALID, SMS_DEVICE_LIMIT.
	SmsDeviceDto createDevice(SmsDeviceCreateRequestDto request);

	// The user's linked phones (never the token).
	List<SmsDeviceDto> listDevices();

	// Removes a linked phone; its token stops working at once. SMS_DEVICE_NOT_FOUND.
	Boolean revokeDevice(IdRequestDto request);

	// Public: the phone's token is in the body. Reads up to 50 messages; a message that is not a bank transaction is counted and dropped (its text is never stored).
	// SMS_TOKEN_INVALID for an unknown or removed token, SMS_PUSH_INVALID for a bad body, SMS_RATE_LIMITED.
	SmsPushResultDto push(SmsPushRequestDto request);
}
