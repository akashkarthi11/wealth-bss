package com.wealth.bss.config;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.admin.AdminClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.wealth.common.logger.KarthiLogger;

// The Kafka brokers, user and password the BSS will use. The deployed ones come from the environment (KAFKA_BOOTSTRAP_SERVERS, KAFKA_USERNAME,
// KAFKA_PASSWORD; KAFKA_SASL_MECHANISM and KAFKA_CA_PATH stay as deployed). An admin can change the servers and the sign-in. The password is never
// returned or logged. Kept in memory, so a restart returns to the deployed settings. Nothing publishes events yet; the event code will read this.
@Component
public class KafkaEndpoint {

	private static final KarthiLogger LOGGER = new KarthiLogger(KafkaEndpoint.class);
	private static final int CHECK_SECONDS = 5;

	private final String defaultServers;
	private volatile String overrideServers;
	private volatile String overrideUsername;
	private volatile String overridePassword;

	public KafkaEndpoint(@Value("${KAFKA_BOOTSTRAP_SERVERS:}") String bootstrapServers) {
		this.defaultServers = bootstrapServers == null ? "" : bootstrapServers.trim();
	}

	public boolean isConfigured() {
		return !current().isEmpty();
	}

	public String defaultServers() {
		return defaultServers;
	}

	public String current() {
		String chosen = overrideServers;
		return chosen == null ? defaultServers : chosen;
	}

	public String username() {
		String chosen = overrideUsername != null ? overrideUsername : System.getenv("KAFKA_USERNAME");
		return chosen == null ? "" : chosen;
	}

	public boolean isPasswordSet() {
		return !password().isEmpty();
	}

	public boolean isCustomised() {
		return overrideServers != null || overrideUsername != null || overridePassword != null;
	}

	// username and password: null keeps the current one.
	public synchronized void set(String servers, String username, String password) {
		this.overrideServers = servers.equals(defaultServers) ? null : servers;
		if (username != null) {
			this.overrideUsername = username;
		}
		if (password != null) {
			this.overridePassword = password;
		}
		LOGGER.info("Kafka settings changed by an admin");
	}

	public synchronized void reset() {
		this.overrideServers = null;
		this.overrideUsername = null;
		this.overridePassword = null;
		LOGGER.info("Kafka settings reset to the deployed ones by an admin");
	}

	private String password() {
		String chosen = overridePassword != null ? overridePassword : System.getenv("KAFKA_PASSWORD");
		return chosen == null ? "" : chosen;
	}

	// The client properties for these servers; username and password null = the current ones.
	public Map<String, Object> clientProperties(String servers, String username, String password) {
		Map<String, Object> props = new HashMap<>();
		props.put("bootstrap.servers", servers);
		String user = username != null ? username : username();
		String pass = password != null ? password : password();
		if (!user.isBlank()) {
			props.put("security.protocol", "SASL_SSL");
			props.put("sasl.mechanism", System.getenv().getOrDefault("KAFKA_SASL_MECHANISM", "SCRAM-SHA-256"));
			props.put("sasl.jaas.config", "org.apache.kafka.common.security.scram.ScramLoginModule required username=\"" + escape(user) + "\" password=\""
					+ escape(pass) + "\";");
			String ca = System.getenv("KAFKA_CA_PATH");
			if (ca != null && !ca.isBlank()) {
				props.put("ssl.truststore.type", "PEM");
				props.put("ssl.truststore.location", ca);
			}
		}
		return props;
	}

	public Map<String, Object> clientProperties(String servers) {
		return clientProperties(servers, null, null);
	}

	// Asks the brokers who is in the cluster; UP when at least one answers in time.
	public boolean isUp(String servers, String username, String password) {
		Map<String, Object> props = clientProperties(servers, username, password);
		props.put("request.timeout.ms", CHECK_SECONDS * 1000);
		props.put("default.api.timeout.ms", CHECK_SECONDS * 1000);
		try (AdminClient admin = AdminClient.create(props)) {
			return !admin.describeCluster().nodes().get(CHECK_SECONDS, TimeUnit.SECONDS).isEmpty();
		} catch (Exception e) {
			LOGGER.warn("Kafka probe failed: " + e.getClass().getSimpleName());
			return false;
		}
	}

	private static String escape(String value) {
		return value.replace("\\", "\\\\").replace("\"", "\\\"");
	}
}
