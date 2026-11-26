package com.wealth.bss.config;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.wealth.common.generic.BaseError;
import com.wealth.common.generic.KarthiException;
import com.wealth.common.logger.KarthiLogger;

// Encrypts the passwords kept in wealth_settings (AES-GCM). The key is WEALTH_CONFIG_KEY in the server environment: base64 of 16, 24 or 32 bytes.
// It is never stored in the table or sent to a screen. Without it a password cannot be saved (the rest of a setting still can).
@Component
public class SettingsCipher {

	private static final KarthiLogger LOGGER = new KarthiLogger(SettingsCipher.class);
	private static final String ALGORITHM = "AES/GCM/NoPadding";
	private static final int IV_BYTES = 12;
	private static final int TAG_BITS = 128;

	private final byte[] key;
	private final SecureRandom random = new SecureRandom();

	public SettingsCipher(@Value("${WEALTH_CONFIG_KEY:}") String base64Key) {
		byte[] decoded = null;
		if (base64Key != null && !base64Key.isBlank()) {
			try {
				decoded = Base64.getDecoder().decode(base64Key.trim());
			} catch (IllegalArgumentException e) {
				LOGGER.warn("WEALTH_CONFIG_KEY is not valid base64; saved passwords cannot be used");
			}
			if (decoded != null && decoded.length != 16 && decoded.length != 24 && decoded.length != 32) {
				LOGGER.warn("WEALTH_CONFIG_KEY must be 16, 24 or 32 bytes; saved passwords cannot be used");
				decoded = null;
			}
		}
		this.key = decoded;
	}

	public boolean isConfigured() {
		return key != null;
	}

	// base64(iv + ciphertext). KarthiException SETTINGS_KEY_MISSING when there is no key.
	public String encrypt(String plain) {
		if (key == null) {
			throw new KarthiException(BaseError.SETTINGS_KEY_MISSING);
		}
		try {
			byte[] iv = new byte[IV_BYTES];
			random.nextBytes(iv);
			Cipher cipher = Cipher.getInstance(ALGORITHM);
			cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, iv));
			byte[] encrypted = cipher.doFinal(plain.getBytes(java.nio.charset.StandardCharsets.UTF_8));
			return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array());
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("The password could not be encrypted", e);
		}
	}

	// The password, or null when there is no key or the text cannot be decrypted with it (a changed key): the deployed password is used then.
	public String decrypt(String stored) {
		if (key == null || stored == null || stored.isBlank()) {
			return null;
		}
		try {
			byte[] all = Base64.getDecoder().decode(stored);
			Cipher cipher = Cipher.getInstance(ALGORITHM);
			cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, all, 0, IV_BYTES));
			return new String(cipher.doFinal(all, IV_BYTES, all.length - IV_BYTES), java.nio.charset.StandardCharsets.UTF_8);
		} catch (GeneralSecurityException | IllegalArgumentException e) {
			LOGGER.warn("A saved password could not be decrypted; the deployed one is used");
			return null;
		}
	}
}
