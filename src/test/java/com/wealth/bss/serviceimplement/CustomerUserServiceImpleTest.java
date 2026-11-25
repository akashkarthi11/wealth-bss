package com.wealth.bss.serviceimplement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import com.wealth.bss.common.model.dao.IfWealthUserDao;
import com.wealth.bss.common.model.dto.PaginationDto;
import com.wealth.bss.common.model.entity.WealthUserCredentialEntity;
import com.wealth.bss.common.model.entity.WealthUserEntity;
import com.wealth.bss.common.utils.security.LoginRateLimiter;
import com.wealth.bss.common.utils.security.PasswordUtil;
import com.wealth.bss.serviceinterface.IfRequestContextService;
import com.wealth.common.client.NexusServiceClient;
import com.wealth.common.generic.BaseError;
import com.wealth.common.generic.KarthiException;
import com.wealth.common.model.dto.ThreadLocalDto;
import com.wealth.common.model.dto.customer.LoginRequestDto;
import com.wealth.common.model.dto.customer.LoginResponseDto;
import com.wealth.common.model.dto.customer.PasswordResetRequestDto;
import com.wealth.common.model.dto.customer.RegisterRequestDto;
import com.wealth.common.model.dto.customer.UserDto;
import com.wealth.common.utils.generic.RequestHeaderUtil;
import com.wealth.common.utils.security.JwtTokenUtil;

class CustomerUserServiceImpleTest {

	private static final String ID = "3f2b8c1e-0a4d-4c55-9d0e-1b6c7a8d9e10";
	private static final String GOOD = "a-long-enough-password";

	private final PasswordUtil passwords = new PasswordUtil();
	private IfWealthUserDao dao;
	private LoginRateLimiter limiter;
	private IfRequestContextService contextService;
	private JwtTokenUtil jwt;
	private NexusServiceClient nexus;
	private CustomerUserServiceImple service;

	@SuppressWarnings("unchecked")
	@BeforeEach
	void setUp() {
		dao = mock(IfWealthUserDao.class);
		limiter = mock(LoginRateLimiter.class);
		contextService = mock(IfRequestContextService.class);
		jwt = mock(JwtTokenUtil.class);
		nexus = mock(NexusServiceClient.class);
		ObjectProvider<NexusServiceClient> provider = mock(ObjectProvider.class);
		when(provider.getIfAvailable()).thenReturn(nexus);
		new RequestHeaderUtil(provider);
		RequestHeaderUtil.setIp("203.0.113.5");
		when(limiter.attempt(anyString(), anyLong(), anyLong())).thenReturn(true);
		when(jwt.generateToken(anyString(), anyMap())).thenReturn("jwt-value");
		service = new CustomerUserServiceImple(dao, passwords, limiter, contextService, jwt, "owner-key", 604800);
	}

	@AfterEach
	void tearDown() {
		RequestHeaderUtil.clear();
	}

	private LoginRequestDto login(String email, String password) {
		LoginRequestDto r = new LoginRequestDto();
		r.setEmail(email);
		r.setPassword(password);
		return r;
	}

	private WealthUserCredentialEntity credential(String role) {
		WealthUserCredentialEntity c = new WealthUserCredentialEntity();
		c.setId(ID);
		c.setEmail("owner@example.com");
		c.setPasswordHash(passwords.hash(GOOD));
		c.setRole(role);
		c.setSessionVersion(3);
		return c;
	}

	private String code(Runnable call) {
		return assertThrows(KarthiException.class, call::run).getMessage();
	}

	@Test
	void loginReturnsATokenWithTheSessionVersionAndClearsTheEmailCounter() {
		when(dao.findCredentialByEmail(any(PaginationDto.class))).thenReturn(credential("user"));
		when(dao.findMenusByRole(any(PaginationDto.class))).thenReturn("overview,banks");
		LoginResponseDto r = service.login(login("  Owner@Example.com ", GOOD));
		assertEquals("jwt-value", r.getToken());
		assertEquals(604800, r.getExpiresIn());
		assertEquals(ID, r.getUser().getId());
		assertEquals(List.of("overview", "banks"), r.getUser().getMenus());
		verify(jwt).generateToken(eq(ID), eq(Map.of("sv", 3)));
		verify(limiter).reset("login:email:owner@example.com");
		verify(dao).findCredentialByEmail(argThat(p -> "owner@example.com".equals(p.getSearchText())));
	}

	@Test
	void adminGetsEveryMenu() {
		when(dao.findCredentialByEmail(any(PaginationDto.class))).thenReturn(credential("admin"));
		assertNull(service.login(login("owner@example.com", GOOD)).getUser().getMenus());
		verify(dao, never()).findMenusByRole(any());
	}

	@Test
	void wrongPasswordAndUnknownEmailGiveTheSameCodeAndCountAnAttempt() {
		when(dao.findCredentialByEmail(any(PaginationDto.class))).thenReturn(credential("user"));
		assertEquals(BaseError.PASSWORD_MISMATCH, code(() -> service.login(login("owner@example.com", "wrong-password-1"))));
		when(dao.findCredentialByEmail(any(PaginationDto.class))).thenReturn(null);
		assertEquals(BaseError.PASSWORD_MISMATCH, code(() -> service.login(login("nobody@example.com", "wrong-password-1"))));
		verify(limiter).attempt(eq("login:email:nobody@example.com"), eq(5L), eq(900L));
		verify(limiter, never()).reset(anyString());
	}

	@Test
	void lockedOutEmailOrIpIsRefusedBeforeAnyLookup() {
		when(limiter.count("login:email:owner@example.com")).thenReturn(5L);
		assertEquals(BaseError.TOO_MANY_LOGIN_ATTEMPTS, code(() -> service.login(login("owner@example.com", GOOD))));
		when(limiter.count("login:email:owner@example.com")).thenReturn(0L);
		when(limiter.count("login:ip:203.0.113.5")).thenReturn(20L);
		assertEquals(BaseError.TOO_MANY_LOGIN_ATTEMPTS, code(() -> service.login(login("owner@example.com", GOOD))));
		verify(dao, never()).findCredentialByEmail(any());
	}

	@Test
	void badInputIsRejectedWithItsOwnCode() {
		assertEquals(BaseError.EMAIL_CANNOT_BE_NULL_OR_EMPTY, code(() -> service.login(login(" ", "x"))));
		assertEquals(BaseError.INVALID_EMAIL_FORMAT, code(() -> service.login(login("no-at-sign", "x"))));
		assertEquals(BaseError.PASSWORD_EMPTY, code(() -> service.login(login("a@b.co", ""))));
		assertEquals(BaseError.PASSWORD_TOO_LONG, code(() -> service.login(login("a@b.co", "x".repeat(201)))));
	}

	private RegisterRequestDto register(String key, String password) {
		RegisterRequestDto r = new RegisterRequestDto();
		r.setEmail("New@Example.com");
		r.setPassword(password);
		r.setSecretKey(key);
		return r;
	}

	@Test
	void registerChecksTheSecretKeyThenThePolicyThenDuplicates() {
		assertEquals(BaseError.SECURITY_KEY_MISMATCH, code(() -> service.register(register("wrong", GOOD))));
		assertEquals(BaseError.PASSWORD_TOO_SHORT, code(() -> service.register(register("owner-key", "short"))));
		when(dao.insertUser(any())).thenReturn(null);
		assertEquals(BaseError.EMAILID_ALREADY_PRESENT, code(() -> service.register(register("owner-key", GOOD))));
	}

	@Test
	void registerStoresALowercaseEmailAndAHashNeverThePassword() {
		WealthUserEntity created = new WealthUserEntity();
		created.setId(ID);
		created.setEmail("new@example.com");
		created.setRole("user");
		created.setSessionVersion(1);
		when(dao.insertUser(any())).thenReturn(created);
		LoginResponseDto r = service.register(register("owner-key", GOOD));
		assertEquals("jwt-value", r.getToken());
		verify(dao).insertUser(argThat(c -> "new@example.com".equals(c.getEmail()) && c.getPasswordHash().startsWith("s1$")
				&& !c.getPasswordHash().contains(GOOD)));
	}

	@Test
	void registrationIsOffWhenNoSecretKeyIsConfigured() {
		CustomerUserServiceImple noKey = new CustomerUserServiceImple(dao, passwords, limiter, contextService, jwt, "", 3600);
		assertEquals(BaseError.SECURITY_KEY_MISMATCH, code(() -> noKey.register(register("", GOOD))));
	}

	private PasswordResetRequestDto reset(String email, String password) {
		PasswordResetRequestDto r = new PasswordResetRequestDto();
		r.setEmail(email);
		r.setNewPassword(password);
		return r;
	}

	private void callerIs(String role) {
		RequestHeaderUtil.setUserId(ID);
		ThreadLocalDto dto = new ThreadLocalDto();
		dto.setUserId(ID);
		dto.setRole(role);
		when(nexus.getRequestHeaderCache(ID)).thenReturn(dto);
	}

	@Test
	void onlyAnAdminCanResetAPassword() {
		callerIs("user");
		assertEquals(BaseError.ADMIN_ONLY_ACTION, code(() -> service.resetPassword(reset("a@b.co", GOOD))));
		verify(dao, never()).updatePassword(any());
	}

	@Test
	void adminResetEvictsTheCacheAndRejectsUnknownEmailsAndWeakPasswords() {
		callerIs("admin");
		WealthUserEntity updated = new WealthUserEntity();
		updated.setId("other-id");
		when(dao.updatePassword(any())).thenReturn(updated);
		service.resetPassword(reset("a@b.co", GOOD));
		verify(contextService).evict("other-id");
		when(dao.updatePassword(any())).thenReturn(null);
		assertEquals(BaseError.USER_NOT_FOUND, code(() -> service.resetPassword(reset("x@b.co", GOOD))));
		assertEquals(BaseError.PASSWORD_TOO_SHORT, code(() -> service.resetPassword(reset("a@b.co", "short"))));
	}

	@Test
	void meReadsTheContextFromRedisAndTheUserFromTheDatabase() {
		callerIs("user");
		WealthUserEntity u = new WealthUserEntity();
		u.setId(ID);
		u.setEmail("owner@example.com");
		u.setRole("user");
		when(dao.findById(argThat(p -> ID.equals(p.getId())))).thenReturn(u);
		when(dao.findMenusByRole(any())).thenReturn(null);
		UserDto dto = service.me();
		assertEquals("owner@example.com", dto.getEmail());
		assertNull(dto.getMenus());
	}

	@Test
	void logoutEvictsTheCallersCache() {
		RequestHeaderUtil.setUserId(ID);
		service.logout();
		verify(contextService).evict(ID);
	}
}
