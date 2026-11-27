package com.wealth.bss.serviceimplement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wealth.bss.config.EnvironmentSettings;
import com.wealth.bss.config.KafkaEndpoint;
import com.wealth.bss.config.PostgresEndpoint;
import com.wealth.bss.config.RedisEndpoint;
import com.wealth.bss.serviceinterface.IfRequestContextService;
import com.wealth.common.generic.BaseError;
import com.wealth.common.generic.KarthiException;
import com.wealth.common.model.dto.admin.ConnectionDto;
import com.wealth.common.model.dto.admin.ConnectionRequestDto;

class EnvironmentServiceImpleTest {

	private IfRequestContextService context;
	private RedisEndpoint redis;
	private KafkaEndpoint kafka;
	private PostgresEndpoint postgres;
	private EnvironmentSettings settings;
	private EnvironmentServiceImple service;

	@BeforeEach
	void setUp() {
		context = mock(IfRequestContextService.class);
		redis = mock(RedisEndpoint.class);
		kafka = mock(KafkaEndpoint.class);
		postgres = mock(PostgresEndpoint.class);
		settings = mock(EnvironmentSettings.class);
		service = new EnvironmentServiceImple(context, redis, kafka, postgres, settings, "the-service-key", "deployed-code");
		when(postgres.current()).thenReturn("db.example.com:5432");
		when(postgres.defaultAddress()).thenReturn("db.example.com:5432");
		when(redis.current()).thenReturn("redis.example.com:6379");
		when(redis.defaultAddress()).thenReturn("redis.example.com:6379");
		when(redis.isConfigured()).thenReturn(true);
		when(redis.isEnabled()).thenReturn(true);
		when(kafka.isEnabled()).thenReturn(true);
		when(kafka.current()).thenReturn("");
		when(kafka.defaultServers()).thenReturn("");
	}

	private ConnectionRequestDto request(String address) {
		ConnectionRequestDto r = new ConnectionRequestDto();
		r.setUrl(address);
		return r;
	}

	private String codeOf(Runnable call) {
		return assertThrows(KarthiException.class, call::run).getMessage();
	}

	@Test
	void everyActionIsForAnAdminOnly() {
		doThrow(new KarthiException(BaseError.ADMIN_ONLY_ACTION)).when(context).requireAdmin();
		assertEquals(BaseError.ADMIN_ONLY_ACTION, codeOf(service::redisGet));
		assertEquals(BaseError.ADMIN_ONLY_ACTION, codeOf(() -> service.redisUpdate(request("a.example.com:6379"))));
		assertEquals(BaseError.ADMIN_ONLY_ACTION, codeOf(service::redisReset));
		assertEquals(BaseError.ADMIN_ONLY_ACTION, codeOf(service::kafkaGet));
		assertEquals(BaseError.ADMIN_ONLY_ACTION, codeOf(() -> service.kafkaUpdate(request("k.example.com:9092"))));
		assertEquals(BaseError.ADMIN_ONLY_ACTION, codeOf(service::kafkaReset));
		verify(redis, never()).set(anyString(), anyInt(), any(), any());
		verify(kafka, never()).set(anyString(), any(), any());
	}

	private ConnectionRequestDto active(Boolean enabled) {
		ConnectionRequestDto r = new ConnectionRequestDto();
		r.setEnabled(enabled);
		return r;
	}

	@Test
	void theActiveSwitchIsForAnAdminOnly() {
		doThrow(new KarthiException(BaseError.ADMIN_ONLY_ACTION)).when(context).requireAdmin();
		assertEquals(BaseError.ADMIN_ONLY_ACTION, codeOf(() -> service.redisActive(active(false))));
		assertEquals(BaseError.ADMIN_ONLY_ACTION, codeOf(() -> service.kafkaActive(active(false))));
		verify(redis, never()).setEnabled(org.mockito.ArgumentMatchers.anyBoolean());
		verify(kafka, never()).setEnabled(org.mockito.ArgumentMatchers.anyBoolean());
	}

	@Test
	void theActiveSwitchNeedsTrueOrFalse() {
		assertEquals(BaseError.PROVIDER_INVALID, codeOf(() -> service.redisActive(active(null))));
		assertEquals(BaseError.PROVIDER_INVALID, codeOf(() -> service.kafkaActive(null)));
		verify(redis, never()).setEnabled(org.mockito.ArgumentMatchers.anyBoolean());
	}

	@Test
	void switchingRedisOffReportsOffWithoutCheckingTheServer() {
		when(redis.isEnabled()).thenReturn(false);
		ConnectionDto dto = service.redisActive(active(false));
		verify(redis).setEnabled(false);
		assertEquals("OFF", dto.getStatus());
		assertEquals(false, dto.isEnabled());
		verify(redis, never()).isUp(anyString(), anyInt(), any(), any());
	}

	@Test
	void switchingRedisOnChecksTheServerAgain() {
		when(redis.isUp("redis.example.com", 6379, null, null)).thenReturn(true);
		ConnectionDto dto = service.redisActive(active(true));
		verify(redis).setEnabled(true);
		assertEquals("UP", dto.getStatus());
		assertTrue(dto.isEnabled());
	}

	private ConnectionRequestDto refreshTime(Integer seconds) {
		ConnectionRequestDto request = new ConnectionRequestDto();
		request.setRefreshSeconds(seconds);
		return request;
	}

	@Test
	void theRefreshTimeIsForAnAdminAndBetweenFiveAndSixtySeconds() {
		when(kafka.refreshSeconds()).thenReturn(30);

		assertEquals(30, service.kafkaRefreshTime(refreshTime(30)).getRefreshSeconds());
		verify(kafka).setRefreshSeconds(30);
		service.kafkaRefreshTime(refreshTime(5));
		service.kafkaRefreshTime(refreshTime(60));
		for (Integer bad : new Integer[] { 4, 61, 0, -1, null }) {
			assertEquals(BaseError.REFRESH_TIME_INVALID, codeOf(() -> service.kafkaRefreshTime(refreshTime(bad))), String.valueOf(bad));
		}
		assertEquals(BaseError.REFRESH_TIME_INVALID, codeOf(() -> service.kafkaRefreshTime(null)));

		doThrow(new KarthiException(BaseError.ADMIN_ONLY_ACTION)).when(context).requireAdmin();
		assertEquals(BaseError.ADMIN_ONLY_ACTION, codeOf(() -> service.kafkaRefreshTime(refreshTime(20))));
		verify(kafka, never()).setRefreshSeconds(20);
	}

	@Test
	void switchingKafkaOffReportsOffAndOnReportsTheLiveStatus() {
		when(kafka.isEnabled()).thenReturn(false);
		assertEquals("OFF", service.kafkaActive(active(false)).getStatus());
		verify(kafka).setEnabled(false);
		verify(kafka, never()).isUp(anyString(), any(), any());

		when(kafka.isEnabled()).thenReturn(true);
		when(kafka.current()).thenReturn("k.example.com:9092");
		when(kafka.isConfigured()).thenReturn(true);
		when(kafka.isUp("k.example.com:9092", null, null)).thenReturn(true);
		assertEquals("UP", service.kafkaActive(active(true)).getStatus());
		verify(kafka).setEnabled(true);
	}

	@Test
	void redisShowsTheAddressTheDeployedOneAndTheLiveStatus() {
		when(redis.isUp("redis.example.com", 6379, null, null)).thenReturn(true);
		ConnectionDto dto = service.redisGet();
		assertEquals("redis.example.com:6379", dto.getUrl());
		assertEquals("UP", dto.getStatus());
	}

	@Test
	void redisWithNoUrlConfiguredIsNotConfigured() {
		when(redis.isConfigured()).thenReturn(false);
		assertEquals("NOT_CONFIGURED", service.redisGet().getStatus());
	}

	@Test
	void redisAddressIsSavedOnlyWhenItAnswers() {
		when(redis.isUp("new.example.com", 6380, null, null)).thenReturn(false);
		assertEquals(BaseError.CONNECTION_NOT_UP, codeOf(() -> service.redisUpdate(request("new.example.com:6380"))));
		verify(redis, never()).set(anyString(), anyInt(), any(), any());

		when(redis.isUp("new.example.com", 6380, null, null)).thenReturn(true);
		service.redisUpdate(request(" new.example.com:6380 "));
		verify(redis).set("new.example.com", 6380, null, null);
	}

	@Test
	void badAddressesAreRefusedBeforeAnythingIsCalled() {
		for (String bad : new String[] { null, "", "host", "host:", ":6379", "host:0", "host:70000", "http://host:80", "host:80/path", "bad host:80",
				"169.254.169.254:80", "-x:80", "host:abc" }) {
			assertEquals(BaseError.ENVIRONMENT_ADDRESS_INVALID, codeOf(() -> service.redisUpdate(request(bad))), String.valueOf(bad));
		}
		verify(redis, never()).isUp(anyString(), anyInt(), any(), any());
	}

	@Test
	void credentialsAreOptionalPassedOnAndValidated() {
		ConnectionRequestDto withLogin = request("new.example.com:6380");
		withLogin.setUsername(" alice ");
		withLogin.setPassword("s3cret value");
		when(redis.isUp("new.example.com", 6380, "alice", "s3cret value")).thenReturn(true);
		service.redisUpdate(withLogin);
		verify(redis).set("new.example.com", 6380, "alice", "s3cret value");

		ConnectionRequestDto bad = request("new.example.com:6380");
		bad.setPassword("a" + (char) 7 + "b");
		assertEquals(BaseError.ENVIRONMENT_CREDENTIALS_INVALID, codeOf(() -> service.redisUpdate(bad)));
		bad.setPassword("x".repeat(201));
		assertEquals(BaseError.ENVIRONMENT_CREDENTIALS_INVALID, codeOf(() -> service.redisUpdate(bad)));
		bad.setPassword(null);
		bad.setUsername("u".repeat(101));
		assertEquals(BaseError.ENVIRONMENT_CREDENTIALS_INVALID, codeOf(() -> service.kafkaUpdate(bad)));
	}

	@Test
	void theInfoShowsTheUserAndWhetherAPasswordIsSetNeverThePassword() {
		when(redis.username()).thenReturn("default");
		when(redis.isPasswordSet()).thenReturn(true);
		ConnectionDto dto = service.redisGet();
		assertEquals("default", dto.getUsername());
		org.junit.jupiter.api.Assertions.assertTrue(dto.isPasswordSet());
	}

	@Test
	void redisResetGoesBackToTheDeployedAddress() {
		service.redisReset();
		verify(redis).reset();
	}

	@Test
	void postgresIsForAnAdminOnly() {
		doThrow(new KarthiException(BaseError.ADMIN_ONLY_ACTION)).when(context).requireAdmin();
		assertEquals(BaseError.ADMIN_ONLY_ACTION, codeOf(service::postgresGet));
		assertEquals(BaseError.ADMIN_ONLY_ACTION, codeOf(() -> service.postgresUpdate(request("db2.example.com:5432"))));
		assertEquals(BaseError.ADMIN_ONLY_ACTION, codeOf(service::postgresReset));
		verify(postgres, never()).change(anyString(), anyInt(), any(), any());
	}

	@Test
	void postgresShowsTheAddressUserAndLiveStatusButNeverThePassword() {
		when(postgres.username()).thenReturn("alice");
		when(postgres.isPasswordSet()).thenReturn(true);
		when(postgres.isUp("db.example.com", 5432, null, null)).thenReturn(true);
		ConnectionDto dto = service.postgresGet();
		assertEquals("db.example.com:5432", dto.getUrl());
		assertEquals("alice", dto.getUsername());
		assertTrue(dto.isPasswordSet());
		assertEquals("UP", dto.getStatus());
	}

	@Test
	void postgresIsChangedOnlyWhenTheNewSettingsConnect() {
		ConnectionRequestDto change = request("db2.example.com:5433");
		change.setUsername("bob");
		change.setPassword("pw");
		when(postgres.change("db2.example.com", 5433, "bob", "pw")).thenReturn(false);
		assertEquals(BaseError.CONNECTION_NOT_UP, codeOf(() -> service.postgresUpdate(change)));

		when(postgres.change("db2.example.com", 5433, "bob", "pw")).thenReturn(true);
		service.postgresUpdate(change);
		assertEquals(BaseError.ENVIRONMENT_ADDRESS_INVALID, codeOf(() -> service.postgresUpdate(request("not an address"))));
	}

	@Test
	void redisAndKafkaChangesAreSavedToTheTableBeforeTheyAreUsedAndResetsWriteTheDeployedValue() {
		when(redis.isUp("new.example.com", 6380, null, null)).thenReturn(true);
		service.redisUpdate(request("new.example.com:6380"));
		org.mockito.InOrder order = org.mockito.Mockito.inOrder(settings, redis);
		order.verify(settings).saveRedis("new.example.com:6380", null, null);
		order.verify(redis).set("new.example.com", 6380, null, null);

		service.redisReset();
		verify(settings).resetRedis();
		service.kafkaReset();
		verify(settings).resetKafka();
	}

	@Test
	void aChangeThatFailsTheCheckIsNotSavedToTheTable() {
		when(redis.isUp("dead.example.com", 6379, null, null)).thenReturn(false);
		codeOf(() -> service.redisUpdate(request("dead.example.com:6379")));
		verify(settings, never()).saveRedis(anyString(), any(), any());
	}

	@Test
	void serviceAddressesAreSavedByAnAdminOnlyAndOnlyForTheTwoKnownKeys() {
		ConnectionRequestDto save = request("http://172.27.99.226:8081/");
		save.setKey("bff_to_bss");
		service.addressSave(save);
		verify(settings).saveAddress("bff_to_bss", "http://172.27.99.226:8081");

		ConnectionRequestDto redisKey = request("http://x:1");
		redisKey.setKey("redis");
		assertEquals(BaseError.INVALID_REQUEST, codeOf(() -> service.addressSave(redisKey)));
		ConnectionRequestDto bad = request("ftp://x");
		bad.setKey("ui_to_bff");
		assertEquals(BaseError.CONNECTION_ADDRESS_INVALID, codeOf(() -> service.addressSave(bad)));
	}

	@Test
	void aServicePicksItsAddressOnlyWithTheSharedKey() {
		ConnectionRequestDto pick = request("http://localhost:8081");
		pick.setKey("bff_to_bss");
		when(settings.pickAddress("bff_to_bss", "http://localhost:8081")).thenReturn("http://172.27.99.226:8081");

		assertEquals("http://172.27.99.226:8081", service.pickAddress("the-service-key", pick).getUrl());
		assertEquals(BaseError.SERVICE_KEY_REJECTED, codeOf(() -> service.pickAddress("wrong", pick)));
		assertEquals(BaseError.SERVICE_KEY_REJECTED, codeOf(() -> service.pickAddress(null, pick)));
		EnvironmentServiceImple noKey = new EnvironmentServiceImple(context, redis, kafka, postgres, settings, "", "");
		assertEquals(BaseError.SERVICE_KEY_REJECTED, codeOf(() -> noKey.pickAddress("", pick)));
	}

	@Test
	void postgresResetGoesBackToTheDeployedSettings() {
		service.postgresReset();
		verify(postgres).reset();
	}

	@Test
	void kafkaWithNoServersIsNotConfigured() {
		assertEquals("NOT_CONFIGURED", service.kafkaGet().getStatus());
	}

	@Test
	void kafkaAcceptsAListAndSavesItOnlyWhenABrokerAnswers() {
		when(kafka.isUp("a.example.com:9092,b.example.com:9092", null, null)).thenReturn(false);
		assertEquals(BaseError.CONNECTION_NOT_UP, codeOf(() -> service.kafkaUpdate(request("a.example.com:9092, b.example.com:9092"))));
		verify(kafka, never()).set(anyString(), any(), any());

		when(kafka.isUp("a.example.com:9092,b.example.com:9092", null, null)).thenReturn(true);
		service.kafkaUpdate(request("a.example.com:9092, b.example.com:9092"));
		verify(kafka).set("a.example.com:9092,b.example.com:9092", null, null);
	}

	@Test
	void kafkaListRulesAreEnforced() {
		assertEquals(BaseError.ENVIRONMENT_ADDRESS_INVALID, codeOf(() -> service.kafkaUpdate(request("a:1,,b:2"))));
		assertEquals(BaseError.ENVIRONMENT_ADDRESS_INVALID, codeOf(() -> service.kafkaUpdate(request("a:1,b:2,c:3,d:4,e:5,f:6"))));
		assertEquals(BaseError.ENVIRONMENT_ADDRESS_INVALID, codeOf(() -> service.kafkaUpdate(request("a:1,169.254.1.1:2"))));
		verify(kafka, never()).isUp(anyString(), any(), any());
	}

	// ---- sign-up access code ----

	private ConnectionRequestDto codeRequest(String text) {
		ConnectionRequestDto r = new ConnectionRequestDto();
		r.setPassword(text);
		return r;
	}

	@Test
	void signupCodeIsSavedOnlyWhenItIsLongEnoughAndClean() {
		when(settings.signupState()).thenReturn("saved");
		when(settings.signupCode("deployed-code")).thenReturn("a-long-enough-code");

		ConnectionDto info = service.signupUpdate(codeRequest("a-long-enough-code"));

		verify(settings).saveSignupCode("a-long-enough-code");
		assertEquals("saved", info.getStatus());
		assertTrue(info.isCustomised());
		assertTrue(info.isPasswordSet());
		assertEquals(BaseError.ACCESS_CODE_TOO_SHORT, codeOf(() -> service.signupUpdate(codeRequest("short"))));
		assertEquals(BaseError.ACCESS_CODE_TOO_SHORT, codeOf(() -> service.signupUpdate(codeRequest(null))));
		assertEquals(BaseError.ENVIRONMENT_CREDENTIALS_INVALID, codeOf(() -> service.signupUpdate(codeRequest("has a hidden\u0007char"))));
		verify(settings, never()).saveSignupCode("short");
	}

	@Test
	void signupCanBeClosedAndPutBackToTheDeployedKey() {
		when(settings.signupState()).thenReturn("closed");
		when(settings.signupCode("deployed-code")).thenReturn("");
		ConnectionDto closed = service.signupClose();
		verify(settings).closeSignup();
		assertEquals("closed", closed.getStatus());
		assertTrue(!closed.isPasswordSet());

		when(settings.signupState()).thenReturn("deployed");
		when(settings.signupCode("deployed-code")).thenReturn("deployed-code");
		ConnectionDto reset = service.signupReset();
		verify(settings).resetSignup();
		assertEquals("deployed", reset.getStatus());
		assertTrue(!reset.isCustomised());
		assertTrue(reset.isPasswordSet());
	}

	@Test
	void theSignupCodeIsAdminOnlyAndNeverReturned() {
		doThrow(new KarthiException(BaseError.ADMIN_ONLY_ACTION)).when(context).requireAdmin();
		assertEquals(BaseError.ADMIN_ONLY_ACTION, codeOf(() -> service.signupGet()));
		assertEquals(BaseError.ADMIN_ONLY_ACTION, codeOf(() -> service.signupUpdate(codeRequest("a-long-enough-code"))));
		assertEquals(BaseError.ADMIN_ONLY_ACTION, codeOf(() -> service.signupClose()));
		assertEquals(BaseError.ADMIN_ONLY_ACTION, codeOf(() -> service.signupReset()));
		verify(settings, never()).saveSignupCode(anyString());
		verify(settings, never()).closeSignup();
		verify(settings, never()).resetSignup();
	}
}
