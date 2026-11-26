package com.wealth.bss.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Base64;

import org.junit.jupiter.api.Test;

import com.wealth.common.generic.BaseError;
import com.wealth.common.generic.KarthiException;

class SettingsCipherTest {

	private static final String KEY = Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());

	@Test
	void aPasswordRoundTripsAndIsNotStoredAsPlainText() {
		SettingsCipher cipher = new SettingsCipher(KEY);
		assertTrue(cipher.isConfigured());
		String stored = cipher.encrypt("s3cret p@ss");
		assertNotEquals("s3cret p@ss", stored);
		assertFalse(stored.contains("s3cret"));
		assertEquals("s3cret p@ss", cipher.decrypt(stored));
		assertNotEquals(stored, cipher.encrypt("s3cret p@ss"), "a fresh random IV each time");
	}

	@Test
	void withoutAKeyAPasswordCannotBeSavedAndNothingIsDecrypted() {
		SettingsCipher none = new SettingsCipher("");
		assertFalse(none.isConfigured());
		assertEquals(BaseError.SETTINGS_KEY_MISSING, assertThrows(KarthiException.class, () -> none.encrypt("x")).getMessage());
		assertNull(none.decrypt("anything"));
		assertFalse(new SettingsCipher("not base64 !!").isConfigured());
		assertFalse(new SettingsCipher(Base64.getEncoder().encodeToString(new byte[10])).isConfigured());
	}

	@Test
	void aChangedKeyOrDamagedTextGivesNothingInsteadOfAnError() {
		String stored = new SettingsCipher(KEY).encrypt("pw");
		SettingsCipher other = new SettingsCipher(Base64.getEncoder().encodeToString("ffffffffffffffffffffffffffffffff".getBytes()));
		assertNull(other.decrypt(stored));
		assertNull(new SettingsCipher(KEY).decrypt("%%%"));
		assertNull(new SettingsCipher(KEY).decrypt(null));
	}
}
