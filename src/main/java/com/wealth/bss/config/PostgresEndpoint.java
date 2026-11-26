package com.wealth.bss.config;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.wealth.common.logger.KarthiLogger;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

// The Postgres host, port, user and password the BSS uses. An admin can point it elsewhere or change the sign-in; the database name and the other
// connection options (for example sslmode) stay as deployed. The password is never returned or logged. A change builds a new pool, tests it, then swaps
// it in. Kept in memory, so a restart returns to the deployed settings.
@Component
@ConditionalOnProperty(name = "wealth.jdbc.url")
public class PostgresEndpoint {

	private static final KarthiLogger LOGGER = new KarthiLogger(PostgresEndpoint.class);
	private static final Pattern URL = Pattern.compile("^jdbc:postgresql://([^/:?]+)(?::(\\d+))?/([^?]*)(?:\\?(.*))?$");
	private static final int DEFAULT_PORT = 5432;
	private static final long CHECK_MILLIS = 10_000;

	private final SwitchableDataSource dataSource;
	private final String database;
	private final String options;
	private final int poolSize;
	private final long connectionTimeoutMs;
	private final String defaultHost;
	private final int defaultPort;
	private final String defaultUsername;
	private final String defaultPassword;
	private volatile String host;
	private volatile int port;
	private volatile String username;
	private volatile String password;
	private volatile boolean changed;

	public PostgresEndpoint(@Value("${wealth.jdbc.url}") String configuredUrl, @Value("${wealth.jdbc.username:}") String configuredUsername,
			@Value("${wealth.jdbc.password:}") String configuredPassword, @Value("${wealth.jdbc.pool-size:5}") int poolSize,
			@Value("${wealth.jdbc.connection-timeout-ms:20000}") long connectionTimeoutMs) {
		String jdbcUrl = configuredUrl.startsWith("postgresql://") ? "jdbc:" + configuredUrl : configuredUrl;
		Matcher parts = URL.matcher(jdbcUrl);
		if (!parts.matches()) {
			throw new IllegalStateException("wealth.jdbc.url is not a jdbc:postgresql://host[:port]/database URL");
		}
		this.defaultHost = parts.group(1);
		this.defaultPort = parts.group(2) == null ? DEFAULT_PORT : Integer.parseInt(parts.group(2));
		this.database = parts.group(3);
		// Options without any user or password: those are kept apart so they can be changed.
		List<String> keep = new ArrayList<>();
		String queryUser = "";
		String queryPassword = "";
		if (parts.group(4) != null) {
			for (String pair : parts.group(4).split("&")) {
				if (pair.startsWith("user=")) {
					queryUser = pair.substring("user=".length());
				} else if (pair.startsWith("password=")) {
					queryPassword = pair.substring("password=".length());
				} else if (!pair.isEmpty()) {
					keep.add(pair);
				}
			}
		}
		this.options = String.join("&", keep);
		this.defaultUsername = !configuredUsername.isBlank() ? configuredUsername : queryUser;
		this.defaultPassword = !configuredPassword.isBlank() ? configuredPassword : queryPassword;
		this.poolSize = Math.max(1, Math.min(poolSize, 10));
		this.connectionTimeoutMs = connectionTimeoutMs;
		this.host = defaultHost;
		this.port = defaultPort;
		this.username = defaultUsername;
		this.password = defaultPassword;
		this.dataSource = new SwitchableDataSource(pool(defaultHost, defaultPort, defaultUsername, defaultPassword, this.connectionTimeoutMs, this.poolSize));
	}

	public SwitchableDataSource dataSource() {
		return dataSource;
	}

	public String defaultAddress() {
		return defaultHost + ":" + defaultPort;
	}

	public String current() {
		return host + ":" + port;
	}

	public String username() {
		return username;
	}

	public boolean isPasswordSet() {
		return !password.isEmpty();
	}

	public boolean isCustomised() {
		return changed;
	}

	// Builds a pool for the new settings (null user or password = the current one), tests it, and only then swaps it in. False when it could not connect.
	public synchronized boolean change(String newHost, int newPort, String newUsername, String newPassword) {
		String user = newUsername != null ? newUsername : username;
		String pass = newPassword != null ? newPassword : password;
		HikariDataSource next = pool(newHost, newPort, user, pass, CHECK_MILLIS, poolSize);
		if (!works(next)) {
			next.close();
			return false;
		}
		apply(next, newHost, newPort, user, pass, true);
		LOGGER.info("Postgres settings changed by an admin");
		return true;
	}

	public synchronized void reset() {
		HikariDataSource next = pool(defaultHost, defaultPort, defaultUsername, defaultPassword, connectionTimeoutMs, poolSize);
		apply(next, defaultHost, defaultPort, defaultUsername, defaultPassword, false);
		LOGGER.info("Postgres settings reset to the deployed ones by an admin");
	}

	// Whether these settings (null user or password = the current one) can open a connection, on a throwaway pool.
	public boolean isUp(String checkHost, int checkPort, String checkUsername, String checkPassword) {
		HikariDataSource probe = pool(checkHost, checkPort, checkUsername != null ? checkUsername : username,
				checkPassword != null ? checkPassword : password, CHECK_MILLIS, 1);
		try {
			return works(probe);
		} finally {
			probe.close();
		}
	}

	private void apply(HikariDataSource next, String newHost, int newPort, String user, String pass, boolean customised) {
		dataSource.swap(next);
		this.host = newHost;
		this.port = newPort;
		this.username = user;
		this.password = pass;
		this.changed = customised;
	}

	private boolean works(HikariDataSource pool) {
		try (Connection connection = pool.getConnection()) {
			return connection.isValid((int) (CHECK_MILLIS / 1000));
		} catch (Exception e) {
			LOGGER.warn("Postgres probe failed: " + e.getClass().getSimpleName());
			return false;
		}
	}

	private HikariDataSource pool(String poolHost, int poolPort, String user, String pass, long timeoutMs, int size) {
		HikariConfig config = new HikariConfig();
		config.setJdbcUrl("jdbc:postgresql://" + poolHost + ":" + poolPort + "/" + database + (options.isEmpty() ? "" : "?" + options));
		if (!user.isEmpty()) {
			config.setUsername(user);
		}
		if (!pass.isEmpty()) {
			config.setPassword(pass);
		}
		config.setMaximumPoolSize(size);
		config.setMinimumIdle(0);
		config.setConnectionTimeout(timeoutMs);
		config.setInitializationFailTimeout(-1);
		config.setPoolName("wealth-bss-postgres");
		return new HikariDataSource(config);
	}
}
