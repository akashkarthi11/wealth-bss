package com.wealth.bss.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.stereotype.Component;

import com.wealth.common.logger.KarthiLogger;

// The Redis host, port, user and password the BSS uses. An admin can point it elsewhere or change the sign-in; the database and TLS setting stay as
// deployed. The password is never returned or logged. Kept in memory, so a restart returns to the deployed settings.
@Component
public class RedisEndpoint {

	private static final KarthiLogger LOGGER = new KarthiLogger(RedisEndpoint.class);

	private final LettuceConnectionFactory factory;
	private final boolean configured;
	private final String defaultHost;
	private final int defaultPort;
	private final String defaultUsername;
	private final RedisPassword defaultPassword;
	private volatile boolean credentialsChanged;

	public RedisEndpoint(RedisConnectionFactory connectionFactory, @Value("${spring.data.redis.url:}") String url) {
		if (!(connectionFactory instanceof LettuceConnectionFactory lettuce)) {
			throw new IllegalStateException("The BSS expects a Lettuce Redis connection");
		}
		this.factory = lettuce;
		this.configured = url != null && !url.isBlank();
		RedisStandaloneConfiguration config = lettuce.getStandaloneConfiguration();
		this.defaultHost = lettuce.getHostName();
		this.defaultPort = lettuce.getPort();
		this.defaultUsername = config.getUsername();
		this.defaultPassword = config.getPassword();
	}

	public boolean isConfigured() {
		return configured;
	}

	public String defaultAddress() {
		return defaultHost + ":" + defaultPort;
	}

	public String current() {
		return factory.getHostName() + ":" + factory.getPort();
	}

	public String username() {
		String user = factory.getStandaloneConfiguration().getUsername();
		return user == null ? "" : user;
	}

	public boolean isPasswordSet() {
		return factory.getStandaloneConfiguration().getPassword().isPresent();
	}

	public boolean isCustomised() {
		return !current().equals(defaultAddress()) || credentialsChanged;
	}

	// Points the live connection at another host and port and, when given, another user and password (null keeps the current one).
	public synchronized void set(String host, int port, String username, String password) {
		RedisStandaloneConfiguration config = factory.getStandaloneConfiguration();
		config.setHostName(host);
		config.setPort(port);
		if (username != null) {
			config.setUsername(username);
			credentialsChanged = true;
		}
		if (password != null) {
			config.setPassword(RedisPassword.of(password));
			credentialsChanged = true;
		}
		factory.stop();
		factory.start();
		LOGGER.info("Redis settings changed by an admin");
	}

	public synchronized void reset() {
		RedisStandaloneConfiguration config = factory.getStandaloneConfiguration();
		config.setHostName(defaultHost);
		config.setPort(defaultPort);
		config.setUsername(defaultUsername);
		config.setPassword(defaultPassword);
		credentialsChanged = false;
		factory.stop();
		factory.start();
		LOGGER.info("Redis settings reset to the deployed ones by an admin");
	}

	// A PING with these settings (null user or password = the current one), on a throwaway connection.
	public boolean isUp(String host, int port, String username, String password) {
		RedisStandaloneConfiguration current = factory.getStandaloneConfiguration();
		RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(host, port);
		config.setUsername(username != null ? username : current.getUsername());
		config.setPassword(password != null ? RedisPassword.of(password) : current.getPassword());
		config.setDatabase(current.getDatabase());
		LettuceConnectionFactory probe = new LettuceConnectionFactory(config, factory.getClientConfiguration());
		try {
			probe.afterPropertiesSet();
			try (RedisConnection connection = probe.getConnection()) {
				return "PONG".equalsIgnoreCase(connection.ping());
			}
		} catch (RuntimeException e) {
			LOGGER.warn("Redis probe failed: " + e.getClass().getSimpleName());
			return false;
		} finally {
			probe.destroy();
		}
	}
}
