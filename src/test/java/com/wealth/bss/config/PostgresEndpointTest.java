package com.wealth.bss.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PostgresEndpointTest {

	@Test
	void theDeployedAddressAndSignInAreReadFromTheUrl() {
		PostgresEndpoint endpoint = new PostgresEndpoint("jdbc:postgresql://db.example.com/neondb?sslmode=require&user=alice&password=secret", "", "", 5, 1000);
		assertEquals("db.example.com:5432", endpoint.current());
		assertEquals("db.example.com:5432", endpoint.defaultAddress());
		assertEquals("alice", endpoint.username());
		assertTrue(endpoint.isPasswordSet());
		assertFalse(endpoint.isCustomised());
		endpoint.dataSource().close();
	}

	@Test
	void thePropertiesWinOverTheUrlAndAPortIsRead() {
		PostgresEndpoint endpoint = new PostgresEndpoint("postgresql://db.example.com:6543/neondb?sslmode=require", "bob", "pw", 5, 1000);
		assertEquals("db.example.com:6543", endpoint.current());
		assertEquals("bob", endpoint.username());
		assertTrue(endpoint.isPasswordSet());
		endpoint.dataSource().close();
	}

	@Test
	void noPasswordMeansNotSet() {
		PostgresEndpoint endpoint = new PostgresEndpoint("jdbc:postgresql://db.example.com/neondb", "", "", 5, 1000);
		assertFalse(endpoint.isPasswordSet());
		assertEquals("", endpoint.username());
		endpoint.dataSource().close();
	}

	@Test
	void aUrlThatIsNotPostgresIsRefusedAtStart() {
		assertThrows(IllegalStateException.class, () -> new PostgresEndpoint("jdbc:mysql://x/y", "", "", 5, 1000));
	}

	@Test
	void anAddressThatCannotConnectIsNotUsedAndTheOldSettingsStay() {
		PostgresEndpoint endpoint = new PostgresEndpoint("jdbc:postgresql://db.example.com/neondb?sslmode=require", "alice", "secret", 5, 1000);
		assertFalse(endpoint.isUp("127.0.0.1", 9, null, null));
		assertFalse(endpoint.change("127.0.0.1", 9, "other", "other-pw"));
		assertEquals("db.example.com:5432", endpoint.current());
		assertEquals("alice", endpoint.username());
		assertFalse(endpoint.isCustomised());
		endpoint.dataSource().close();
	}

	@Test
	void resetRestoresTheDeployedSettings() {
		PostgresEndpoint endpoint = new PostgresEndpoint("jdbc:postgresql://db.example.com/neondb", "alice", "secret", 5, 1000);
		endpoint.reset();
		assertEquals("db.example.com:5432", endpoint.current());
		assertEquals("alice", endpoint.username());
		assertFalse(endpoint.isCustomised());
		endpoint.dataSource().close();
	}
}
