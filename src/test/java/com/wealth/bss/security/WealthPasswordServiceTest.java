package com.wealth.bss.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WealthPasswordServiceTest {
    private final WealthPasswordService passwords = new WealthPasswordService();

    @Test
    void verifiesExistingNodeScryptHash() {
        String existingHash = "s1$AAECAwQFBgcICQoLDA0ODw$11kKyiyYAc8G7rp3KmncMc44YlkdllIqxOa7pq0fMaUtb3NvK4WtqmJiM16xEuVvAU9BejfXS-De92abLFHCng";
        assertTrue(passwords.matches("correct horse battery staple", existingHash));
        assertFalse(passwords.matches("incorrect password", existingHash));
    }

    @Test
    void createsAndVerifiesWealthCompatibleHash() {
        String hash = passwords.hash("a-long-enough-password");
        assertTrue(hash.startsWith("s1$"));
        assertTrue(passwords.matches("a-long-enough-password", hash));
        assertFalse(passwords.matches("different-password", hash));
    }

    @Test
    void enforcesExistingPasswordPolicy() {
        assertEquals("Password must be at least 10 characters.", passwords.passwordProblem("short"));
        assertNull(passwords.passwordProblem("1234567890"));
        assertEquals("Password is too long.", passwords.passwordProblem("x".repeat(201)));
    }
}
