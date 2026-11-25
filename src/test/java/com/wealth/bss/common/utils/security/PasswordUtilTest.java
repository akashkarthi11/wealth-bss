package com.wealth.bss.common.utils.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.wealth.common.generic.BaseError;

class PasswordUtilTest {

	private final PasswordUtil passwords = new PasswordUtil();

	@Test
	void verifiesAHashMadeByTheNodeApp() {
		String nodeHash = "s1$AAECAwQFBgcICQoLDA0ODw$11kKyiyYAc8G7rp3KmncMc44YlkdllIqxOa7pq0fMaUtb3NvK4WtqmJiM16xEuVvAU9BejfXS-De92abLFHCng";
		assertTrue(passwords.matches("correct horse battery staple", nodeHash));
		assertFalse(passwords.matches("incorrect password", nodeHash));
	}

	@Test
	void createsAHashTheSameFormatAndVerifiesIt() {
		String hash = passwords.hash("a-long-enough-password");
		assertTrue(hash.startsWith("s1$"));
		assertTrue(passwords.matches("a-long-enough-password", hash));
		assertFalse(passwords.matches("different-password", hash));
	}

	@Test
	void garbageHashesNeverMatch() {
		assertFalse(passwords.matches("x", "not-a-hash"));
		assertFalse(passwords.matches("x", "s2$a$b"));
		assertFalse(passwords.matches("x", ""));
	}

	@Test
	void policyReturnsTheErrorCode() {
		assertEquals(BaseError.PASSWORD_TOO_SHORT, passwords.passwordProblem("short"));
		assertNull(passwords.passwordProblem("1234567890"));
		assertEquals(BaseError.PASSWORD_TOO_LONG, passwords.passwordProblem("x".repeat(201)));
	}
}
