package com.wealth.bss.serviceimplement;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.wealth.bss.common.model.dao.IfWealthUserDao;
import com.wealth.bss.common.model.dto.PaginationDto;
import com.wealth.bss.common.model.entity.WealthUserCredentialEntity;
import com.wealth.bss.common.model.entity.WealthUserEntity;
import com.wealth.bss.common.utils.security.LoginRateLimiter;
import com.wealth.bss.common.utils.security.PasswordUtil;
import com.wealth.bss.constants.WealthServiceConstants;
import com.wealth.bss.serviceinterface.IfCustomerUserService;
import com.wealth.bss.serviceinterface.IfRequestContextService;
import com.wealth.common.assembler.GenericAssembler;
import com.wealth.common.generic.BaseError;
import com.wealth.common.generic.KarthiException;
import com.wealth.common.logger.KarthiLogger;
import com.wealth.common.model.dto.ThreadLocalDto;
import com.wealth.common.model.dto.customer.LoginRequestDto;
import com.wealth.common.model.dto.customer.LoginResponseDto;
import com.wealth.common.model.dto.customer.PasswordResetRequestDto;
import com.wealth.common.model.dto.customer.RegisterRequestDto;
import com.wealth.common.model.dto.customer.UserDto;
import com.wealth.common.utils.generic.RequestHeaderUtil;
import com.wealth.common.utils.security.JwtTokenUtil;

// Rules carried over from the Node auth route: lowercase email, 5/20 attempts per 15 minutes, owner-only reset.
@Service
public class CustomerUserServiceImple implements IfCustomerUserService {

	private static final KarthiLogger LOGGER = new KarthiLogger(CustomerUserServiceImple.class);
	private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
	// Verified against for an unknown email, so a missing user costs the same time as a wrong password.
	private static final String TIMING_HASH = "s1$AAECAwQFBgcICQoLDA0ODw$11kKyiyYAc8G7rp3KmncMc44YlkdllIqxOa7pq0fMaUtb3NvK4WtqmJiM16xEuVvAU9BejfXS-De92abLFHCng";

	private final IfWealthUserDao wealthUserDao;
	private final PasswordUtil passwordUtil;
	private final LoginRateLimiter rateLimiter;
	private final IfRequestContextService requestContextService;
	private final JwtTokenUtil jwtTokenUtil;
	private final String accessKey;
	private final long tokenLifetimeSeconds;

	public CustomerUserServiceImple(IfWealthUserDao wealthUserDao, PasswordUtil passwordUtil,
			LoginRateLimiter rateLimiter, IfRequestContextService requestContextService, JwtTokenUtil jwtTokenUtil,
			@Value("${wealth.access.key:}") String accessKey,
			@Value("${jwt.access-token-lifetime-seconds:3600}") long tokenLifetimeSeconds) {
		this.wealthUserDao = wealthUserDao;
		this.passwordUtil = passwordUtil;
		this.rateLimiter = rateLimiter;
		this.requestContextService = requestContextService;
		this.jwtTokenUtil = jwtTokenUtil;
		this.accessKey = accessKey;
		this.tokenLifetimeSeconds = tokenLifetimeSeconds;
	}

	@Override
	public LoginResponseDto login(LoginRequestDto request) {
		String methodName = "login";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		String email = requireEmail(request == null ? null : request.getEmail());
		String password = requirePassword(request.getPassword());
		String ip = String.valueOf(RequestHeaderUtil.getIp());
		String ipKey = WealthServiceConstants.LOGIN_IP_KEY_PREFIX + ip;
		String emailKey = WealthServiceConstants.LOGIN_EMAIL_KEY_PREFIX + email;
		if (rateLimiter.count(ipKey) >= WealthServiceConstants.LOGIN_IP_LIMIT
				|| rateLimiter.count(emailKey) >= WealthServiceConstants.LOGIN_EMAIL_LIMIT) {
			throw new KarthiException(BaseError.TOO_MANY_LOGIN_ATTEMPTS);
		}
		PaginationDto paginationDto = new PaginationDto();
		paginationDto.setSearchText(email);
		WealthUserCredentialEntity user = wealthUserDao.findCredentialByEmail(paginationDto);
		boolean ok = user != null && passwordUtil.matches(password, user.getPasswordHash());
		if (user == null) {
			passwordUtil.matches(password, TIMING_HASH);
		}
		if (!ok) {
			rateLimiter.attempt(ipKey, WealthServiceConstants.LOGIN_IP_LIMIT, WealthServiceConstants.AUTH_WINDOW_SECONDS);
			rateLimiter.attempt(emailKey, WealthServiceConstants.LOGIN_EMAIL_LIMIT, WealthServiceConstants.AUTH_WINDOW_SECONDS);
			LOGGER.info(WealthServiceConstants.EXITING + methodName + WealthServiceConstants.LOG_REJECTED);
			throw new KarthiException(BaseError.PASSWORD_MISMATCH);
		}
		rateLimiter.reset(emailKey);
		LOGGER.info(WealthServiceConstants.EXITING + methodName + WealthServiceConstants.LOG_SIGNED_IN);
		return signedIn(GenericAssembler.toDTO(user, UserDto.class), user.getSessionVersion());
	}

	@Override
	public LoginResponseDto register(RegisterRequestDto request) {
		String methodName = "register";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		String email = requireEmail(request == null ? null : request.getEmail());
		String password = requirePassword(request.getPassword());
		limitAuthPerIp();
		if (!secretKeyMatches(request.getSecretKey())) {
			throw new KarthiException(BaseError.SECURITY_KEY_MISMATCH);
		}
		throwOnPasswordProblem(password);
		WealthUserEntity created = wealthUserDao.insertUser(credentialFor(email, password));
		if (created == null) {
			throw new KarthiException(BaseError.EMAILID_ALREADY_PRESENT);
		}
		LOGGER.info(WealthServiceConstants.EXITING + methodName);
		return signedIn(GenericAssembler.toDTO(created, UserDto.class), created.getSessionVersion());
	}

	@Override
	public UserDto me() {
		ThreadLocalDto context = RequestHeaderUtil.getThreadLocalDto();
		if (context == null) {
			throw new KarthiException(BaseError.USER_NOT_FOUND);
		}
		PaginationDto paginationDto = new PaginationDto();
		paginationDto.setId(context.getUserId());
		WealthUserEntity user = wealthUserDao.findById(paginationDto);
		if (user == null) {
			throw new KarthiException(BaseError.USER_NOT_FOUND);
		}
		UserDto dto = GenericAssembler.toDTO(user, UserDto.class);
		dto.setMenus(menusFor(user.getRole()));
		return dto;
	}

	@Override
	public void logout() {
		requestContextService.evict(RequestHeaderUtil.getUserId());
	}

	@Override
	public void resetPassword(PasswordResetRequestDto request) {
		String methodName = "resetPassword";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		requestContextService.requireAdmin();
		String email = requireEmail(request == null ? null : request.getEmail());
		String newPassword = requirePassword(request.getNewPassword());
		limitAuthPerIp();
		throwOnPasswordProblem(newPassword);
		WealthUserEntity updated = wealthUserDao.updatePassword(credentialFor(email, newPassword));
		if (updated == null) {
			throw new KarthiException(BaseError.USER_NOT_FOUND);
		}
		requestContextService.evict(updated.getId());
		LOGGER.info(WealthServiceConstants.EXITING + methodName);
	}

	private LoginResponseDto signedIn(UserDto user, Integer sessionVersion) {
		user.setMenus(menusFor(user.getRole()));
		String token = jwtTokenUtil.generateToken(user.getId(),
				Map.of(WealthServiceConstants.SESSION_VERSION_CLAIM, sessionVersion));
		LoginResponseDto response = new LoginResponseDto();
		response.setToken(token);
		response.setExpiresIn(tokenLifetimeSeconds);
		response.setUser(user);
		return response;
	}

	// null means every menu (admin, or a role with nothing saved).
	private List<String> menusFor(String role) {
		if (WealthServiceConstants.ROLE_ADMIN.equals(role)) {
			return null;
		}
		PaginationDto paginationDto = new PaginationDto();
		paginationDto.setSearchText(role);
		String csv = wealthUserDao.findMenusByRole(paginationDto);
		return csv == null || csv.isBlank() ? null : Arrays.asList(csv.split(","));
	}

	private void limitAuthPerIp() {
		if (!rateLimiter.attempt(WealthServiceConstants.AUTH_IP_KEY_PREFIX + RequestHeaderUtil.getIp(),
				WealthServiceConstants.AUTH_IP_LIMIT,
				WealthServiceConstants.AUTH_WINDOW_SECONDS)) {
			throw new KarthiException(BaseError.TOO_MANY_LOGIN_ATTEMPTS);
		}
	}

	private WealthUserCredentialEntity credentialFor(String email, String password) {
		WealthUserCredentialEntity credential = new WealthUserCredentialEntity();
		credential.setEmail(email);
		credential.setPasswordHash(passwordUtil.hash(password));
		return credential;
	}

	private boolean secretKeyMatches(String supplied) {
		if (accessKey == null || accessKey.isBlank() || supplied == null) {
			return false;
		}
		return MessageDigest.isEqual(supplied.getBytes(StandardCharsets.UTF_8), accessKey.getBytes(StandardCharsets.UTF_8));
	}

	private void throwOnPasswordProblem(String password) {
		String problem = passwordUtil.passwordProblem(password);
		if (problem != null) {
			throw new KarthiException(problem);
		}
	}

	private static String requireEmail(String raw) {
		String email = raw == null ? "" : raw.trim().toLowerCase();
		if (email.isEmpty()) {
			throw new KarthiException(BaseError.EMAIL_CANNOT_BE_NULL_OR_EMPTY);
		}
		if (email.length() > WealthServiceConstants.EMAIL_MAX_LENGTH || !EMAIL.matcher(email).matches()) {
			throw new KarthiException(BaseError.INVALID_EMAIL_FORMAT);
		}
		return email;
	}

	private static String requirePassword(String password) {
		if (password == null || password.isEmpty()) {
			throw new KarthiException(BaseError.PASSWORD_EMPTY);
		}
		if (password.length() > WealthServiceConstants.PASSWORD_MAX_LENGTH) {
			throw new KarthiException(BaseError.PASSWORD_TOO_LONG);
		}
		return password;
	}
}
