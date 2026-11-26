package com.wealth.bss.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class KafkaEndpointTest {

	@Test
	void noServersMeansNotConfigured() {
		KafkaEndpoint endpoint = new KafkaEndpoint("");
		assertFalse(endpoint.isConfigured());
		assertEquals("", endpoint.current());
	}

	@Test
	void theDeployedListIsTheDefaultAndACustomOneCanBeSetAndReset() {
		KafkaEndpoint endpoint = new KafkaEndpoint(" a.example.com:9092 ");
		assertEquals("a.example.com:9092", endpoint.current());
		assertFalse(endpoint.isCustomised());

		endpoint.set("b.example.com:9093", null, null);
		assertEquals("b.example.com:9093", endpoint.current());
		assertTrue(endpoint.isCustomised());
		assertEquals("a.example.com:9092", endpoint.defaultServers());

		endpoint.set("a.example.com:9092", null, null);
		assertFalse(endpoint.isCustomised());

		endpoint.set("b.example.com:9093", null, null);
		endpoint.reset();
		assertEquals("a.example.com:9092", endpoint.current());
	}

	@Test
	void aNewUserAndPasswordGoIntoTheClientPropertiesAndResetClearsThem() {
		KafkaEndpoint endpoint = new KafkaEndpoint("a.example.com:9092");
		endpoint.set("a.example.com:9092", "alice", "p\"w");
		assertEquals("alice", endpoint.username());
		assertTrue(endpoint.isPasswordSet());
		assertTrue(endpoint.isCustomised());
		Object jaas = endpoint.clientProperties("a.example.com:9092").get("sasl.jaas.config");
		assertTrue(String.valueOf(jaas).contains("username=\"alice\"") && String.valueOf(jaas).contains("password=\"p\\\"w\""));
		assertEquals("SASL_SSL", endpoint.clientProperties("a.example.com:9092").get("security.protocol"));

		endpoint.reset();
		assertFalse(endpoint.isCustomised());
	}

	@Test
	void theClientPropertiesCarryTheServers() {
		assertEquals("x.example.com:9092", new KafkaEndpoint("").clientProperties("x.example.com:9092").get("bootstrap.servers"));
	}

	@Test
	void anUnreachableBrokerIsNotUp() {
		assertFalse(new KafkaEndpoint("").isUp("127.0.0.1:9", null, null));
	}
}
