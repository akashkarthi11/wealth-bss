package com.wealth.bss.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wealth.bss.common.model.dao.IfWealthSettingDao;
import com.wealth.bss.common.model.entity.WealthSettingEntity;

class EnvironmentSettingsTest {

	private static final String KEY = Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());

	private IfWealthSettingDao dao;
	private SettingsCipher cipher;
	private RedisEndpoint redis;
	private KafkaEndpoint kafka;
	private EnvironmentSettings settings;

	@BeforeEach
	void setUp() {
		dao = mock(IfWealthSettingDao.class);
		cipher = new SettingsCipher(KEY);
		redis = mock(RedisEndpoint.class);
		kafka = mock(KafkaEndpoint.class);
		settings = new EnvironmentSettings(dao, cipher, redis, kafka);
		when(redis.defaultAddress()).thenReturn("redis.example.com:6379");
		when(redis.current()).thenReturn("redis.example.com:6379");
		when(kafka.defaultServers()).thenReturn("");
	}

	private WealthSettingEntity row(String key, String value, String username, String passwordEnc) {
		WealthSettingEntity e = new WealthSettingEntity();
		e.setSettingKey(key);
		e.setSettingValue(value);
		e.setUsername(username);
		e.setPasswordEnc(passwordEnc);
		return e;
	}

	@Test
	void atBootTheTableWinsAndIsHeldInMemory() {
		when(dao.findAll()).thenReturn(List.of(row("redis", "other.example.com:6380", "alice", cipher.encrypt("pw")), row("kafka", "b1:9092,b2:9092", null, null)));

		settings.pickAtBoot();

		verify(redis).set("other.example.com", 6380, "alice", "pw");
		verify(kafka).set("b1:9092,b2:9092", null, null);
	}

	@Test
	void atBootAMissingRowIsCreatedFromTheDeployedSettingBySystem() {
		when(dao.findAll()).thenReturn(List.of());

		settings.pickAtBoot();

		verify(dao).insertIfMissing(argThat(e -> "redis".equals(e.getSettingKey()) && "redis.example.com:6379".equals(e.getSettingValue()) && "cron".equals(e.getActor())));
		verify(dao).insertIfMissing(argThat(e -> "kafka".equals(e.getSettingKey()) && " ".equals(e.getSettingValue())));
		verify(redis, never()).set(any(), anyInt(), any(), any());
	}

	@Test
	void atBootASavedRowThatMatchesWhatIsRunningChangesNothing() {
		when(dao.findAll()).thenReturn(List.of(row("redis", "redis.example.com:6379", null, null), row("kafka", " ", null, null)));
		settings.pickAtBoot();
		verify(redis, never()).set(any(), anyInt(), any(), any());
		verify(kafka, never()).set(any(), any(), any());
	}

	@Test
	void ifTheTableCannotBeReadTheDeployedSettingsStayAndBootContinues() {
		doThrow(new IllegalStateException("table missing")).when(dao).findAll();
		settings.pickAtBoot();
		verify(redis, never()).set(any(), anyInt(), any(), any());
	}

	@Test
	void aSavedPasswordIsEncryptedAndAnEmptyOneKeepsTheStoredOne() {
		String firstCipher = cipher.encrypt("first");
		when(dao.findAll()).thenReturn(List.of(row("redis", "old:6379", "bob", firstCipher)));

		settings.saveRedis("new.example.com:6380", null, null);

		verify(dao).upsert(argThat(e -> "new.example.com:6380".equals(e.getSettingValue()) && "bob".equals(e.getUsername()) && firstCipher.equals(e.getPasswordEnc())));

		settings.saveRedis("new.example.com:6380", "carol", "second");

		verify(dao).upsert(argThat(e -> "carol".equals(e.getUsername()) && e.getPasswordEnc() != null && !"second".equals(e.getPasswordEnc())
				&& "second".equals(cipher.decrypt(e.getPasswordEnc()))));
	}

	@Test
	void aResetWritesTheDeployedValueWithNoLoginOfItsOwn() {
		settings.resetRedis();
		verify(dao).upsert(argThat(e -> "redis".equals(e.getSettingKey()) && "redis.example.com:6379".equals(e.getSettingValue()) && e.getUsername() == null
				&& e.getPasswordEnc() == null));
	}

	@Test
	void serviceAddressesArePickedFromTheTableOrSeededWithTheDeployedOne() {
		when(dao.insertIfMissing(argThat(e -> e != null && "bff_to_bss".equals(e.getSettingKey())))).thenReturn(row("bff_to_bss", "http://172.27.99.226:8081", null, null));
		assertEquals("http://172.27.99.226:8081", settings.pickAddress("bff_to_bss", "http://localhost:8081"));

		when(dao.insertIfMissing(argThat(e -> e != null && "ui_to_bff".equals(e.getSettingKey())))).thenReturn(null);
		assertEquals("http://localhost:8080", settings.pickAddress("ui_to_bff", "http://localhost:8080"));
	}

	@Test
	void aSavedSignupCodeIsStoredEncryptedAndReadBack() {
		settings.saveSignupCode("a-long-enough-code");
		verify(dao).upsert(argThat(e -> "signup_code".equals(e.getSettingKey()) && "saved".equals(e.getSettingValue()) && e.getPasswordEnc() != null
				&& !e.getPasswordEnc().contains("a-long-enough-code")));

		String stored = cipher.encrypt("a-long-enough-code");
		when(dao.findAll()).thenReturn(List.of(row("signup_code", "saved", null, stored)));
		assertEquals("a-long-enough-code", settings.signupCode("deployed"));
		assertEquals("saved", settings.signupState());
	}

	@Test
	void signupFallsBackToTheDeployedKeyUnlessClosed() {
		when(dao.findAll()).thenReturn(List.of());
		assertEquals("deployed-key", settings.signupCode("deployed-key"));
		assertEquals("", settings.signupCode(null));
		assertEquals("deployed", settings.signupState());

		when(dao.findAll()).thenReturn(List.of(row("signup_code", "deployed", null, null)));
		assertEquals("deployed-key", settings.signupCode("deployed-key"));

		when(dao.findAll()).thenReturn(List.of(row("signup_code", "closed", null, null)));
		assertEquals("", settings.signupCode("deployed-key"));
		assertEquals("closed", settings.signupState());
	}

	@Test
	void signupIsClosedWhenTheSavedCodeCannotBeReadOrTheTableFails() {
		SettingsCipher otherKey = new SettingsCipher(Base64.getEncoder().encodeToString("ffffffffffffffffffffffffffffffff".getBytes()));
		when(dao.findAll()).thenReturn(List.of(row("signup_code", "saved", null, otherKey.encrypt("a-long-enough-code"))));
		assertEquals("", settings.signupCode("deployed-key"));

		when(dao.findAll()).thenThrow(new IllegalStateException("db down"));
		assertEquals("", settings.signupCode("deployed-key"));
	}

	@Test
	void closingOrResettingSignupKeepsNoCode() {
		settings.closeSignup();
		verify(dao).upsert(argThat(e -> "signup_code".equals(e.getSettingKey()) && "closed".equals(e.getSettingValue()) && e.getPasswordEnc() == null));
		settings.resetSignup();
		verify(dao).upsert(argThat(e -> "signup_code".equals(e.getSettingKey()) && "deployed".equals(e.getSettingValue()) && e.getPasswordEnc() == null));
	}
}
