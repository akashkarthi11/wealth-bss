package com.wealth.bss.serviceimplement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.wealth.bss.common.model.dao.IfWealthAccountDao;
import com.wealth.bss.common.model.dao.IfWealthSmsDeviceDao;
import com.wealth.bss.common.model.dto.PaginationDto;
import com.wealth.bss.common.model.entity.WealthAccountEntity;
import com.wealth.bss.common.model.entity.WealthSmsDeviceEntity;
import com.wealth.bss.common.utils.security.LoginRateLimiter;
import com.wealth.bss.serviceinterface.IfBankImportService;
import com.wealth.common.generic.BaseError;
import com.wealth.common.generic.KarthiException;
import com.wealth.common.model.dto.IdRequestDto;
import com.wealth.common.model.dto.sms.SmsDeviceCreateRequestDto;
import com.wealth.common.model.dto.sms.SmsDeviceDto;
import com.wealth.common.model.dto.sms.SmsMessageDto;
import com.wealth.common.model.dto.sms.SmsPushRequestDto;
import com.wealth.common.model.dto.sms.SmsPushResultDto;
import com.wealth.common.model.dto.sms.SmsTokenRequestDto;
import com.wealth.common.model.dto.transaction.BankImportResultDto;
import com.wealth.common.model.dto.transaction.BankLineDto;
import com.wealth.common.utils.generic.RequestHeaderUtil;

// Linking a phone and reading its bank SMS, with the DAO and the import mocked.
class SmsServiceTest {

	private static final String USER = "2a7014a8-9cf0-4d39-bed2-c83a83eddf0c";
	private static final String DEVICE = "71111111-1111-4111-8111-111111111111";
	private static final String TOKEN = "abcdefghijklmnopqrstuvwxyz0123456789ABCDEFG";
	private static final String SMS = "Sent Rs.250.00\nFrom HDFC Bank A/c *1234\nTo SWIGGY\nOn 28/11/26\nRef 633245678901";

	private IfWealthSmsDeviceDao dao;
	private IfWealthAccountDao accounts;
	private IfBankImportService importer;
	private LoginRateLimiter limiter;
	private SmsServiceImple service;
	private final Instant now = Instant.parse("2026-11-28T05:00:00Z");

	@BeforeEach
	void setUp() {
		dao = mock(IfWealthSmsDeviceDao.class);
		accounts = mock(IfWealthAccountDao.class);
		importer = mock(IfBankImportService.class);
		limiter = mock(LoginRateLimiter.class);
		when(limiter.attempt(anyString(), anyLong(), anyLong())).thenReturn(true);
		service = new SmsServiceImple(dao, accounts, importer, limiter, Clock.fixed(now, ZoneId.of("Asia/Kolkata")));
		RequestHeaderUtil.setUserId(USER);
		RequestHeaderUtil.setIp("10.0.0.9");
	}

	@AfterEach
	void tearDown() {
		RequestHeaderUtil.clear();
	}

	private static SmsDeviceCreateRequestDto create(String label) {
		SmsDeviceCreateRequestDto request = new SmsDeviceCreateRequestDto();
		request.setLabel(label);
		return request;
	}

	private static SmsMessageDto message(String sender, String body, long at) {
		SmsMessageDto message = new SmsMessageDto();
		message.setId("1");
		message.setSender(sender);
		message.setBody(body);
		message.setAt(at);
		return message;
	}

	private static SmsPushRequestDto push(String token, SmsMessageDto... messages) {
		SmsPushRequestDto request = new SmsPushRequestDto();
		request.setToken(token);
		request.setMessages(new ArrayList<>(List.of(messages)));
		return request;
	}

	private WealthSmsDeviceEntity device() {
		WealthSmsDeviceEntity entity = new WealthSmsDeviceEntity();
		entity.setId(DEVICE);
		entity.setUserId(USER);
		return entity;
	}

	@Test
	void linkingGivesATokenOnceAndKeepsOnlyItsHash() {
		when(dao.countActive(USER)).thenReturn(0);
		when(dao.insert(eq(USER), eq("My phone"), anyString())).thenReturn(DEVICE);
		SmsDeviceDto dto = service.createDevice(create("  My phone "));
		assertEquals(DEVICE, dto.getId());
		assertEquals(43, dto.getToken().length());
		ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
		verify(dao).insert(eq(USER), eq("My phone"), hash.capture());
		assertNotEquals(dto.getToken(), hash.getValue());
		assertEquals(SmsServiceImple.hashOf(dto.getToken()), hash.getValue());
	}

	@Test
	void linkingNeedsAnameAndRoom() {
		assertEquals(BaseError.SMS_LABEL_INVALID, assertThrows(KarthiException.class, () -> service.createDevice(create("  "))).getMessage());
		assertEquals(BaseError.SMS_LABEL_INVALID, assertThrows(KarthiException.class, () -> service.createDevice(create("x".repeat(41)))).getMessage());
		when(dao.countActive(USER)).thenReturn(5);
		assertEquals(BaseError.SMS_DEVICE_LIMIT, assertThrows(KarthiException.class, () -> service.createDevice(create("Second"))).getMessage());
		verify(dao, never()).insert(anyString(), anyString(), anyString());
	}

	@Test
	void theListNeverCarriesATokenAndRevokeChecksTheId() {
		WealthSmsDeviceEntity row = device();
		row.setLabel("My phone");
		row.setCreatedAt(Timestamp.from(now));
		row.setLastRead(3);
		row.setLastSaved(2);
		when(dao.findActive(USER)).thenReturn(List.of(row));
		SmsDeviceDto dto = service.listDevices().get(0);
		assertNull(dto.getToken());
		assertEquals(2, dto.getLastSaved());
		assertEquals(BaseError.SMS_DEVICE_NOT_FOUND, assertThrows(KarthiException.class, () -> revoke("not-an-id")).getMessage());
		when(dao.revoke(USER, DEVICE)).thenReturn(false);
		assertEquals(BaseError.SMS_DEVICE_NOT_FOUND, assertThrows(KarthiException.class, () -> revoke(DEVICE)).getMessage());
		when(dao.revoke(USER, DEVICE)).thenReturn(true);
		assertTrue(revoke(DEVICE));
	}

	private Boolean revoke(String id) {
		IdRequestDto request = new IdRequestDto();
		request.setId(id);
		return service.revokeDevice(request);
	}

	@Test
	void anUnknownTokenGetsOneGenericRefusal() {
		RequestHeaderUtil.setUserId(null);
		assertEquals(BaseError.SMS_TOKEN_INVALID, assertThrows(KarthiException.class, () -> service.push(push("short", message("VM-HDFCBK-S", SMS, 1)))).getMessage());
		when(dao.findByTokenHash(any())).thenReturn(null);
		assertEquals(BaseError.SMS_TOKEN_INVALID, assertThrows(KarthiException.class, () -> service.push(push(TOKEN, message("VM-HDFCBK-S", SMS, 1)))).getMessage());
		verify(importer, never()).importLines(anyString(), any());
	}

	private static WealthAccountEntity account(String bank, String status) {
		WealthAccountEntity entity = new WealthAccountEntity();
		entity.setBankName(bank);
		entity.setStatus(status);
		return entity;
	}

	@Test
	void thePhoneGetsTheOwnersActiveBanksOnceEachAndNeverWithoutItsToken() {
		RequestHeaderUtil.setUserId(null);
		when(dao.findByTokenHash(SmsServiceImple.hashOf(TOKEN))).thenReturn(device());
		ArgumentCaptor<PaginationDto> asked = ArgumentCaptor.forClass(PaginationDto.class);
		when(accounts.findByUser(asked.capture())).thenReturn(new ArrayList<>(List.of(account("ICICI", "active"), account("HDFC ", "active"), account("hdfc", "active"),
				account("Axis", "closed"), account(" ", "active"))));
		SmsTokenRequestDto request = new SmsTokenRequestDto();
		request.setToken(TOKEN);
		assertEquals(List.of("HDFC", "hdfc", "ICICI"), service.banks(request));
		assertEquals(USER, asked.getValue().getUserId());

		request.setToken("short");
		assertEquals(BaseError.SMS_TOKEN_INVALID, assertThrows(KarthiException.class, () -> service.banks(request)).getMessage());
		assertEquals(BaseError.SMS_TOKEN_INVALID, assertThrows(KarthiException.class, () -> service.banks(null)).getMessage());
	}

	@Test
	void manyWrongTokensFromOneAddressAreStopped() {
		when(limiter.attempt(eq("smsbad:10.0.0.9"), anyLong(), anyLong())).thenReturn(false);
		assertEquals(BaseError.SMS_RATE_LIMITED, assertThrows(KarthiException.class, () -> service.push(push(TOKEN, message("VM-HDFCBK-S", SMS, 1)))).getMessage());
	}

	@Test
	void aBankSmsBecomesALineForTheOwnerAndTheUserIsClearedAfter() {
		RequestHeaderUtil.setUserId(null);
		when(dao.findByTokenHash(SmsServiceImple.hashOf(TOKEN))).thenReturn(device());
		BankImportResultDto imported = new BankImportResultDto();
		imported.setStored(1);
		String[] seen = new String[1];
		when(importer.importLines(eq("alert"), any())).thenAnswer(call -> {
			seen[0] = RequestHeaderUtil.getUserId();
			return imported;
		});
		long at = Instant.parse("2026-11-28T04:30:00Z").toEpochMilli();
		SmsPushResultDto result = service.push(push(TOKEN, message("VM-HDFCBK-S", SMS, at), message("VK-AMAZON", "Rs.5 debited", at)));
		assertEquals(USER, seen[0]);
		assertNull(RequestHeaderUtil.getUserId());
		assertEquals(2, result.getRead());
		assertEquals(1, result.getSaved());
		assertEquals(1, result.getSkipped());
		verify(dao).touch(DEVICE, 2, 1);
		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<BankLineDto>> lines = ArgumentCaptor.forClass(List.class);
		verify(importer).importLines(eq("alert"), lines.capture());
		BankLineDto line = lines.getValue().get(0);
		assertTrue(line.getRef().startsWith("sms:"));
		assertEquals("HDFC", line.getBank());
		assertEquals("1234", line.getAccountDigits());
		assertEquals("2026-11-28", line.getDay());
		assertEquals("SWIGGY", line.getDescription());
		assertTrue(line.isDebit());
		assertTrue(line.isSure());
		assertFalse(line.isSuspicious());
	}

	@Test
	void theSameSmsSentTwiceHasTheSameReference() {
		RequestHeaderUtil.setUserId(null);
		when(dao.findByTokenHash(any())).thenReturn(device());
		when(importer.importLines(anyString(), any())).thenReturn(new BankImportResultDto());
		long at = Instant.parse("2026-11-28T04:30:00Z").toEpochMilli();
		service.push(push(TOKEN, message("VM-HDFCBK-S", SMS, at)));
		service.push(push(TOKEN, message("VM-HDFCBK-S", SMS, at)));
		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<BankLineDto>> lines = ArgumentCaptor.forClass(List.class);
		verify(importer, org.mockito.Mockito.times(2)).importLines(anyString(), lines.capture());
		assertEquals(lines.getAllValues().get(0).get(0).getRef(), lines.getAllValues().get(1).get(0).getRef());
	}

	@Test
	void aBadBodyIsRefusedAndNothingIsImported() {
		RequestHeaderUtil.setUserId(null);
		when(dao.findByTokenHash(any())).thenReturn(device());
		SmsPushRequestDto empty = push(TOKEN);
		assertEquals(BaseError.SMS_PUSH_INVALID, assertThrows(KarthiException.class, () -> service.push(empty)).getMessage());
		SmsMessageDto[] many = new SmsMessageDto[51];
		for (int i = 0; i < many.length; i++) {
			many[i] = message("VM-HDFCBK-S", SMS, 1);
		}
		assertEquals(BaseError.SMS_PUSH_INVALID, assertThrows(KarthiException.class, () -> service.push(push(TOKEN, many))).getMessage());
		verify(importer, never()).importLines(anyString(), any());
		verify(dao, never()).touch(anyString(), anyInt(), anyInt());
	}

	@Test
	void aTooLongOrFutureSmsIsSkippedNotImported() {
		RequestHeaderUtil.setUserId(null);
		when(dao.findByTokenHash(any())).thenReturn(device());
		long future = Instant.parse("2026-12-30T04:30:00Z").toEpochMilli();
		SmsPushResultDto result = service.push(push(TOKEN, message("VM-HDFCBK-S", "x".repeat(1001), 1), message("VM-HDFCBK-S", SMS, future)));
		assertEquals(2, result.getSkipped());
		verify(importer, never()).importLines(anyString(), any());
	}
}
