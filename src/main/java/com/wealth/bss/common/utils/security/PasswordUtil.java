package com.wealth.bss.common.utils.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import org.bouncycastle.crypto.generators.SCrypt;
import org.springframework.stereotype.Component;

import com.wealth.bss.constants.WealthServiceConstants;
import com.wealth.common.generic.BaseError;

// Same format as the Node app ("s1$salt$hash", scrypt N=16384 r=8 p=1), so existing passwords keep working.
@Component
public class PasswordUtil {

	private static final SecureRandom RANDOM = new SecureRandom();
	private static final int SALT_BYTES = 16;
	private static final int KEY_BYTES = 64;

	public String hash(String password) {
		byte[] salt = new byte[SALT_BYTES];
		RANDOM.nextBytes(salt);
		byte[] derived = SCrypt.generate(password.getBytes(StandardCharsets.UTF_8), salt, 16384, 8, 1, KEY_BYTES);
		Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
		return "s1$" + encoder.encodeToString(salt) + "$" + encoder.encodeToString(derived);
	}

	public boolean matches(String password, String encoded) {
		try {
			String[] parts = encoded.split("[$]", -1);
			if (parts.length != 3 || !"s1".equals(parts[0])) {
				return false;
			}
			Base64.Decoder decoder = Base64.getUrlDecoder();
			byte[] salt = decoder.decode(parts[1]);
			byte[] expected = decoder.decode(parts[2]);
			if (salt.length != SALT_BYTES || expected.length != KEY_BYTES) {
				return false;
			}
			byte[] actual = SCrypt.generate(password.getBytes(StandardCharsets.UTF_8), salt, 16384, 8, 1,
					expected.length);
			return MessageDigest.isEqual(expected, actual);
		} catch (Exception ignored) {
			return false;
		}
	}

	// Returns an error code, or null when the password is acceptable.
	public String passwordProblem(String password) {
		if (password == null || password.length() < WealthServiceConstants.PASSWORD_MIN_LENGTH) {
			return BaseError.PASSWORD_TOO_SHORT;
		}
		if (password.length() > WealthServiceConstants.PASSWORD_MAX_LENGTH) {
			return BaseError.PASSWORD_TOO_LONG;
		}
		return null;
	}
}
