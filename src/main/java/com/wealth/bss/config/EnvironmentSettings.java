package com.wealth.bss.config;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.wealth.bss.common.model.dao.IfWealthSettingDao;
import com.wealth.bss.common.model.entity.WealthSettingEntity;
import com.wealth.bss.constants.WealthServiceConstants;
import com.wealth.common.logger.KarthiLogger;

// The table wealth_settings is the source of truth for Redis, Kafka and the two service addresses. When the BSS boots it picks Redis and Kafka from
// the table and holds them in memory (RedisEndpoint, KafkaEndpoint); a missing row is created from the deployed setting. Every change an admin
// makes is saved to the table first. Postgres is not here: the BSS needs it to read the table.
@Component
public class EnvironmentSettings {

	private static final KarthiLogger LOGGER = new KarthiLogger(EnvironmentSettings.class);
	private static final Pattern HOST_PORT = Pattern.compile("^([^:]+):(\\d{1,5})$");

	private final IfWealthSettingDao dao;
	private final SettingsCipher cipher;
	private final RedisEndpoint redis;
	private final KafkaEndpoint kafka;

	public EnvironmentSettings(IfWealthSettingDao dao, SettingsCipher cipher, RedisEndpoint redis, KafkaEndpoint kafka) {
		this.dao = dao;
		this.cipher = cipher;
		this.redis = redis;
		this.kafka = kafka;
	}

	// Boot: read the table once and hold what it says. If the table cannot be read the deployed settings stay in use.
	@EventListener(ApplicationReadyEvent.class)
	public void pickAtBoot() {
		try {
			List<WealthSettingEntity> rows = dao.findAll();
			pickRedis(find(rows, WealthServiceConstants.SETTING_REDIS));
			pickKafka(find(rows, WealthServiceConstants.SETTING_KAFKA));
			LOGGER.info("Environment settings picked from wealth_settings");
		} catch (RuntimeException e) {
			LOGGER.warn("Environment settings could not be read from wealth_settings; the deployed ones are used: " + e.getClass().getSimpleName());
		}
	}

	private void pickRedis(Optional<WealthSettingEntity> row) {
		if (row.isEmpty()) {
			dao.insertIfMissing(atBoot(entity(WealthServiceConstants.SETTING_REDIS, redis.defaultAddress(), null, null)));
			return;
		}
		Matcher address = HOST_PORT.matcher(row.get().getSettingValue());
		if (!address.matches()) {
			LOGGER.warn("The saved Redis address is not host:port; the deployed one is used");
			return;
		}
		String host = address.group(1);
		int port = Integer.parseInt(address.group(2));
		String username = row.get().getUsername();
		String password = cipher.decrypt(row.get().getPasswordEnc());
		boolean same = (host + ":" + port).equals(redis.current()) && username == null && password == null;
		if (!same) {
			redis.set(host, port, username, password);
		}
	}

	private void pickKafka(Optional<WealthSettingEntity> row) {
		if (row.isEmpty()) {
			dao.insertIfMissing(atBoot(entity(WealthServiceConstants.SETTING_KAFKA, notSetAsSpace(kafka.defaultServers()), null, null)));
			return;
		}
		String servers = row.get().getSettingValue().trim();
		if (!servers.isEmpty()) {
			kafka.set(servers, row.get().getUsername(), cipher.decrypt(row.get().getPasswordEnc()));
		}
	}

	// ---- saving: called with the new settings after they were checked, before they are used ----

	public void saveRedis(String address, String username, String password) {
		save(WealthServiceConstants.SETTING_REDIS, address, username, password);
	}

	public void resetRedis() {
		reset(WealthServiceConstants.SETTING_REDIS, redis.defaultAddress());
	}

	public void saveKafka(String servers, String username, String password) {
		save(WealthServiceConstants.SETTING_KAFKA, servers, username, password);
	}

	public void resetKafka() {
		reset(WealthServiceConstants.SETTING_KAFKA, notSetAsSpace(kafka.defaultServers()));
	}

	// A service address (ui_to_bff or bff_to_bss): the callers save the address after they checked it.
	public void saveAddress(String key, String url) {
		dao.upsert(entity(key, url, null, null));
	}

	// ---- the sign-up access code: saved (encrypted), closed, or the deployed WEALTH_ACCESS_KEY ----

	public void saveSignupCode(String code) {
		dao.upsert(entity(WealthServiceConstants.SETTING_SIGNUP_CODE, WealthServiceConstants.SIGNUP_SAVED, null, cipher.encrypt(code)));
	}

	public void closeSignup() {
		dao.upsert(entity(WealthServiceConstants.SETTING_SIGNUP_CODE, WealthServiceConstants.SIGNUP_CLOSED, null, null));
	}

	public void resetSignup() {
		dao.upsert(entity(WealthServiceConstants.SETTING_SIGNUP_CODE, WealthServiceConstants.SIGNUP_DEPLOYED, null, null));
	}

	// saved, closed or deployed (no row counts as deployed).
	public String signupState() {
		return find(dao.findAll(), WealthServiceConstants.SETTING_SIGNUP_CODE).map(WealthSettingEntity::getSettingValue)
				.filter(value -> WealthServiceConstants.SIGNUP_SAVED.equals(value) || WealthServiceConstants.SIGNUP_CLOSED.equals(value)).orElse(WealthServiceConstants.SIGNUP_DEPLOYED);
	}

	// The code a new user must give; empty means sign-up is closed. A saved code that cannot be read closes it too.
	public String signupCode(String deployedKey) {
		try {
			Optional<WealthSettingEntity> row = find(dao.findAll(), WealthServiceConstants.SETTING_SIGNUP_CODE);
			String state = row.map(WealthSettingEntity::getSettingValue).orElse(WealthServiceConstants.SIGNUP_DEPLOYED);
			if (WealthServiceConstants.SIGNUP_CLOSED.equals(state)) {
				return "";
			}
			if (WealthServiceConstants.SIGNUP_SAVED.equals(state)) {
				String code = cipher.decrypt(row.get().getPasswordEnc());
				return code == null ? "" : code;
			}
		} catch (RuntimeException e) {
			LOGGER.warn("The sign-up code could not be read; sign-up is closed: " + e.getClass().getSimpleName());
			return "";
		}
		return deployedKey == null ? "" : deployedKey;
	}

	// The address a service should use: the saved one, or its deployed one saved now when there is none yet.
	public String pickAddress(String key, String deployedUrl) {
		WealthSettingEntity row = dao.insertIfMissing(atBoot(entity(key, deployedUrl, null, null)));
		return row == null ? deployedUrl : row.getSettingValue();
	}

	// An empty username or password keeps what the row has now; a new password is stored encrypted.
	private void save(String key, String value, String username, String password) {
		WealthSettingEntity existing = find(dao.findAll(), key).orElse(null);
		String keptUser = username != null ? username : existing == null ? null : existing.getUsername();
		String keptPassword = password != null ? cipher.encrypt(password) : existing == null ? null : existing.getPasswordEnc();
		dao.upsert(entity(key, value, keptUser, keptPassword));
	}

	// Back to the deployed setting: its address and no username or password of its own.
	private void reset(String key, String deployedValue) {
		dao.upsert(entity(key, deployedValue, null, null));
	}

	private Optional<WealthSettingEntity> find(List<WealthSettingEntity> rows, String key) {
		return rows.stream().filter(row -> key.equals(row.getSettingKey())).findFirst();
	}

	// The table needs a value; a space stands for "no Kafka servers set".
	private String notSetAsSpace(String servers) {
		return servers.isEmpty() ? " " : servers;
	}

	// Boot and service-to-service writes carry no user: the audit trigger records them as the system user 1.
	private WealthSettingEntity atBoot(WealthSettingEntity entity) {
		entity.setActor(WealthServiceConstants.ACTOR_CRON);
		return entity;
	}

	private WealthSettingEntity entity(String key, String value, String username, String passwordEnc) {
		WealthSettingEntity entity = new WealthSettingEntity();
		entity.setSettingKey(key);
		entity.setSettingValue(value);
		entity.setUsername(username);
		entity.setPasswordEnc(passwordEnc);
		return entity;
	}
}
