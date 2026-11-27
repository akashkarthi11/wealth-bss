package com.wealth.bss.serviceimplement;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.wealth.bss.config.EnvironmentSettings;
import com.wealth.bss.config.KafkaEndpoint;
import com.wealth.bss.config.PostgresEndpoint;
import com.wealth.bss.config.RedisEndpoint;
import com.wealth.bss.constants.WealthServiceConstants;
import com.wealth.bss.serviceinterface.IfEnvironmentService;
import com.wealth.bss.serviceinterface.IfRequestContextService;
import com.wealth.common.generic.BaseError;
import com.wealth.common.generic.KarthiException;
import com.wealth.common.logger.KarthiLogger;
import com.wealth.common.model.dto.admin.ConnectionDto;
import com.wealth.common.model.dto.admin.ConnectionRequestDto;

@Service
public class EnvironmentServiceImple implements IfEnvironmentService {

	private static final KarthiLogger LOGGER = new KarthiLogger(EnvironmentServiceImple.class);
	private static final Pattern HOST_PORT = Pattern.compile("^([A-Za-z0-9]([A-Za-z0-9.-]*[A-Za-z0-9])?):(\\d{1,5})$");
	private static final int MAX_PORT = 65535;
	private static final Pattern ORIGIN = Pattern.compile("^(https?)://([A-Za-z0-9](?:[A-Za-z0-9.-]*[A-Za-z0-9])?(?::\\d{1,5})?)$");
	private static final Pattern CONTROL = Pattern.compile("\\p{Cntrl}");

	private final IfRequestContextService requestContextService;
	private final RedisEndpoint redis;
	private final KafkaEndpoint kafka;
	private final PostgresEndpoint postgres;
	private final EnvironmentSettings settings;
	private final String serviceKey;

	public EnvironmentServiceImple(IfRequestContextService requestContextService, RedisEndpoint redis, KafkaEndpoint kafka, PostgresEndpoint postgres, EnvironmentSettings settings,
			@Value("${WEALTH_SERVICE_KEY:}") String serviceKey) {
		this.requestContextService = requestContextService;
		this.redis = redis;
		this.kafka = kafka;
		this.postgres = postgres;
		this.settings = settings;
		this.serviceKey = serviceKey == null ? "" : serviceKey;
	}

	@Override
	public ConnectionDto redisGet() {
		requestContextService.requireAdmin();
		return redisInfo();
	}

	@Override
	public ConnectionDto redisUpdate(ConnectionRequestDto request) {
		String methodName = "redisUpdate";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		requestContextService.requireAdmin();
		Matcher address = parseOne(request == null ? null : request.getUrl());
		String host = address.group(1);
		int port = Integer.parseInt(address.group(3));
		String username = username(request);
		String password = password(request);
		if (!redis.isUp(host, port, username, password)) {
			throw new KarthiException(BaseError.CONNECTION_NOT_UP);
		}
		settings.saveRedis(host + ":" + port, username, password);
		redis.set(host, port, username, password);
		LOGGER.info(WealthServiceConstants.EXITING + methodName);
		return redisInfo();
	}

	@Override
	public ConnectionDto redisReset() {
		requestContextService.requireAdmin();
		settings.resetRedis();
		redis.reset();
		return redisInfo();
	}

	@Override
	public ConnectionDto redisActive(ConnectionRequestDto request) {
		String methodName = "redisActive";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		requestContextService.requireAdmin();
		redis.setEnabled(switchValue(request));
		LOGGER.info(WealthServiceConstants.EXITING + methodName);
		return redisInfo();
	}

	@Override
	public ConnectionDto kafkaActive(ConnectionRequestDto request) {
		String methodName = "kafkaActive";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		requestContextService.requireAdmin();
		kafka.setEnabled(switchValue(request));
		LOGGER.info(WealthServiceConstants.EXITING + methodName);
		return kafkaInfo();
	}

	@Override
	public ConnectionDto kafkaRefreshTime(ConnectionRequestDto request) {
		String methodName = "kafkaRefreshTime";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		requestContextService.requireAdmin();
		Integer seconds = request == null ? null : request.getRefreshSeconds();
		if (seconds == null || seconds < WealthServiceConstants.NEXUS_REFRESH_MIN_SECONDS || seconds > WealthServiceConstants.NEXUS_REFRESH_MAX_SECONDS) {
			throw new KarthiException(BaseError.REFRESH_TIME_INVALID);
		}
		kafka.setRefreshSeconds(seconds);
		LOGGER.info(WealthServiceConstants.EXITING + methodName);
		return kafkaInfo();
	}

	@Override
	public ConnectionDto kafkaGet() {
		requestContextService.requireAdmin();
		return kafkaInfo();
	}

	@Override
	public ConnectionDto kafkaUpdate(ConnectionRequestDto request) {
		String methodName = "kafkaUpdate";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		requestContextService.requireAdmin();
		String servers = parseList(request == null ? null : request.getUrl());
		String username = username(request);
		String password = password(request);
		if (!kafka.isUp(servers, username, password)) {
			throw new KarthiException(BaseError.CONNECTION_NOT_UP);
		}
		settings.saveKafka(servers, username, password);
		kafka.set(servers, username, password);
		LOGGER.info(WealthServiceConstants.EXITING + methodName);
		return kafkaInfo();
	}

	@Override
	public ConnectionDto kafkaReset() {
		requestContextService.requireAdmin();
		settings.resetKafka();
		kafka.reset();
		return kafkaInfo();
	}

	@Override
	public ConnectionDto postgresGet() {
		requestContextService.requireAdmin();
		return postgresInfo();
	}

	@Override
	public ConnectionDto postgresUpdate(ConnectionRequestDto request) {
		String methodName = "postgresUpdate";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		requestContextService.requireAdmin();
		Matcher address = parseOne(request == null ? null : request.getUrl());
		String username = username(request);
		String password = password(request);
		// change() builds a pool for the new settings, tests it and swaps it in only when it connects.
		if (!postgres.change(address.group(1), Integer.parseInt(address.group(3)), username, password)) {
			throw new KarthiException(BaseError.CONNECTION_NOT_UP);
		}
		LOGGER.info(WealthServiceConstants.EXITING + methodName);
		return postgresInfo();
	}

	@Override
	public ConnectionDto postgresReset() {
		requestContextService.requireAdmin();
		postgres.reset();
		return postgresInfo();
	}

	@Override
	public ConnectionDto addressSave(ConnectionRequestDto request) {
		requestContextService.requireAdmin();
		String key = serviceAddressKey(request);
		String url = origin(request.getUrl());
		settings.saveAddress(key, url);
		return addressInfo(url);
	}

	@Override
	public ConnectionDto pickAddress(String presentedKey, ConnectionRequestDto request) {
		// Another service, not a user: it proves itself with the shared key. With no key set on this server the call is always refused.
		if (serviceKey.isEmpty() || presentedKey == null
				|| !MessageDigest.isEqual(serviceKey.getBytes(StandardCharsets.UTF_8), presentedKey.getBytes(StandardCharsets.UTF_8))) {
			throw new KarthiException(BaseError.SERVICE_KEY_REJECTED);
		}
		String key = serviceAddressKey(request);
		return addressInfo(settings.pickAddress(key, origin(request.getUrl())));
	}

	private ConnectionDto addressInfo(String url) {
		ConnectionDto dto = new ConnectionDto();
		dto.setUrl(url);
		return dto;
	}

	// Only the two service addresses can be saved or picked this way: Redis and Kafka have their own actions and hold passwords.
	private String serviceAddressKey(ConnectionRequestDto request) {
		String key = request == null ? null : request.getKey();
		if (!WealthServiceConstants.SETTING_UI_TO_BFF.equals(key) && !WealthServiceConstants.SETTING_BFF_TO_BSS.equals(key)) {
			throw new KarthiException(BaseError.INVALID_REQUEST);
		}
		return key;
	}

	// http(s)://host[:port], nothing else.
	private String origin(String text) {
		String url = text == null ? "" : text.trim().replaceAll("/+$", "");
		Matcher matcher = ORIGIN.matcher(url);
		if (!matcher.matches() || matcher.group(2).startsWith(WealthServiceConstants.LINK_LOCAL_PREFIX) || url.length() > WealthServiceConstants.ORIGIN_MAX_LENGTH) {
			throw new KarthiException(BaseError.CONNECTION_ADDRESS_INVALID);
		}
		return url;
	}

	private ConnectionDto postgresInfo() {
		ConnectionDto dto = new ConnectionDto();
		dto.setUrl(postgres.current());
		dto.setDefaultUrl(postgres.defaultAddress());
		dto.setCustomised(postgres.isCustomised());
		dto.setUsername(postgres.username());
		dto.setPasswordSet(postgres.isPasswordSet());
		Matcher now = HOST_PORT.matcher(dto.getUrl());
		dto.setStatus(now.matches() && postgres.isUp(now.group(1), Integer.parseInt(now.group(3)), null, null) ? WealthServiceConstants.STATUS_UP
				: WealthServiceConstants.STATUS_DOWN);
		return dto;
	}

	private ConnectionDto redisInfo() {
		ConnectionDto dto = new ConnectionDto();
		dto.setUrl(redis.current());
		dto.setDefaultUrl(redis.defaultAddress());
		dto.setCustomised(redis.isCustomised());
		dto.setUsername(redis.username());
		dto.setPasswordSet(redis.isPasswordSet());
		dto.setEnabled(redis.isEnabled());
		if (!redis.isEnabled()) {
			dto.setStatus(WealthServiceConstants.STATUS_OFF);
		} else if (!redis.isConfigured()) {
			dto.setStatus(WealthServiceConstants.STATUS_NOT_CONFIGURED);
		} else {
			Matcher now = HOST_PORT.matcher(dto.getUrl());
			dto.setStatus(now.matches() && redis.isUp(now.group(1), Integer.parseInt(now.group(3)), null, null) ? WealthServiceConstants.STATUS_UP
					: WealthServiceConstants.STATUS_DOWN);
		}
		return dto;
	}

	private ConnectionDto kafkaInfo() {
		ConnectionDto dto = new ConnectionDto();
		dto.setUrl(kafka.current());
		dto.setDefaultUrl(kafka.defaultServers());
		dto.setCustomised(kafka.isCustomised());
		dto.setUsername(kafka.username());
		dto.setPasswordSet(kafka.isPasswordSet());
		dto.setEnabled(kafka.isEnabled());
		dto.setRefreshSeconds(kafka.refreshSeconds());
		dto.setStatus(!kafka.isEnabled() ? WealthServiceConstants.STATUS_OFF
				: !kafka.isConfigured() ? WealthServiceConstants.STATUS_NOT_CONFIGURED
				: kafka.isUp(dto.getUrl(), null, null) ? WealthServiceConstants.STATUS_UP : WealthServiceConstants.STATUS_DOWN);
		return dto;
	}

	// The Active / Inactive switch: the request must say true or false.
	private boolean switchValue(ConnectionRequestDto request) {
		if (request == null || request.getEnabled() == null) {
			throw new KarthiException(BaseError.PROVIDER_INVALID);
		}
		return request.getEnabled();
	}

	// Empty means keep what is set now (null); too long or with hidden characters is refused.
	private String username(ConnectionRequestDto request) {
		String text = request == null || request.getUsername() == null ? "" : request.getUsername().trim();
		return credential(text, WealthServiceConstants.USERNAME_MAX_LENGTH);
	}

	private String password(ConnectionRequestDto request) {
		return credential(request == null || request.getPassword() == null ? "" : request.getPassword(), WealthServiceConstants.PASSWORD_MAX_LENGTH);
	}

	private String credential(String text, int max) {
		if (text.isEmpty()) {
			return null;
		}
		if (text.length() > max || CONTROL.matcher(text).find()) {
			throw new KarthiException(BaseError.ENVIRONMENT_CREDENTIALS_INVALID);
		}
		return text;
	}

	// One host:port, or the BAD address error. A link-local address (cloud metadata) is never accepted.
	private Matcher parseOne(String text) {
		Matcher matcher = HOST_PORT.matcher(text == null ? "" : text.trim());
		if (!matcher.matches() || matcher.group(1).startsWith(WealthServiceConstants.LINK_LOCAL_PREFIX)) {
			throw new KarthiException(BaseError.ENVIRONMENT_ADDRESS_INVALID);
		}
		int port = Integer.parseInt(matcher.group(3));
		if (port < 1 || port > MAX_PORT) {
			throw new KarthiException(BaseError.ENVIRONMENT_ADDRESS_INVALID);
		}
		return matcher;
	}

	private String parseList(String text) {
		List<String> servers = new ArrayList<>();
		for (String part : (text == null ? "" : text).split(",")) {
			Matcher one = parseOne(part);
			servers.add(one.group(1) + ":" + one.group(3));
		}
		if (servers.size() > WealthServiceConstants.KAFKA_SERVERS_MAX) {
			throw new KarthiException(BaseError.ENVIRONMENT_ADDRESS_INVALID);
		}
		return String.join(",", servers);
	}
}
