package com.wealth.bss.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

class RedisEndpointTest {

	private LettuceConnectionFactory factory() {
		RedisStandaloneConfiguration config = new RedisStandaloneConfiguration("redis.example.com", 6379);
		config.setPassword(RedisPassword.of("secret"));
		LettuceConnectionFactory factory = new LettuceConnectionFactory(config);
		factory.afterPropertiesSet();
		factory.start();
		return factory;
	}

	@Test
	void theDeployedAddressIsTheDefaultAndANewOneCanBeSetAndReset() {
		LettuceConnectionFactory factory = factory();
		RedisEndpoint endpoint = new RedisEndpoint(factory, "redis://default:secret@redis.example.com:6379");
		assertEquals("redis.example.com:6379", endpoint.current());
		assertFalse(endpoint.isCustomised());

		endpoint.set("other.example.com", 6380, null, null);
		assertEquals("other.example.com:6380", endpoint.current());
		assertEquals("other.example.com", factory.getHostName());
		assertEquals(6380, factory.getPort());
		assertTrue(endpoint.isCustomised());
		assertEquals("redis.example.com:6379", endpoint.defaultAddress());

		endpoint.reset();
		assertEquals("redis.example.com:6379", endpoint.current());
		assertFalse(endpoint.isCustomised());
		factory.destroy();
	}

	@Test
	void thePasswordIsKeptWhenTheAddressChanges() {
		LettuceConnectionFactory factory = factory();
		RedisEndpoint endpoint = new RedisEndpoint(factory, "redis://default:secret@redis.example.com:6379");
		endpoint.set("other.example.com", 6380, null, null);
		assertEquals("secret", new String(factory.getStandaloneConfiguration().getPassword().get()));
		factory.destroy();
	}

	@Test
	void aNewUserAndPasswordAreUsedAndNeverReturnedAndResetRestoresTheDeployedOnes() {
		LettuceConnectionFactory factory = factory();
		RedisEndpoint endpoint = new RedisEndpoint(factory, "redis://default:secret@redis.example.com:6379");
		assertTrue(endpoint.isPasswordSet());
		assertFalse(endpoint.isCustomised());

		endpoint.set("redis.example.com", 6379, "alice", "new-secret");
		assertEquals("alice", endpoint.username());
		assertEquals("new-secret", new String(factory.getStandaloneConfiguration().getPassword().get()));
		assertTrue(endpoint.isCustomised());

		endpoint.reset();
		assertEquals("secret", new String(factory.getStandaloneConfiguration().getPassword().get()));
		assertFalse(endpoint.isCustomised());
		factory.destroy();
	}

	@Test
	void noUrlMeansNotConfigured() {
		LettuceConnectionFactory factory = factory();
		assertFalse(new RedisEndpoint(factory, "").isConfigured());
		assertTrue(new RedisEndpoint(factory, "redis://x").isConfigured());
		factory.destroy();
	}

	@Test
	void anUnreachableAddressIsNotUp() {
		LettuceConnectionFactory factory = factory();
		assertFalse(new RedisEndpoint(factory, "redis://x").isUp("127.0.0.1", 9, null, null));
		factory.destroy();
	}
}
