package com.wealth.bss.constants;

public final class WealthServiceConstants {

	private WealthServiceConstants() {
	}

	public static final String ENTERING = "Entering ";
	public static final String EXITING = "Exiting ";
	public static final String LOG_REJECTED = " | rejected";
	public static final String LOG_SIGNED_IN = " | signed in";

	public static final String REQUEST_CONTEXT_KEY_PREFIX = "requestContext:";

	// Rate-limit keys in Redis.
	public static final String LOGIN_IP_KEY_PREFIX = "login:ip:";
	public static final String LOGIN_EMAIL_KEY_PREFIX = "login:email:";
	public static final String AUTH_IP_KEY_PREFIX = "auth:ip:";

	// Paths of the BSS; the BFF calls the same strings through its Feign client.
	public static final String USERS_BASE = "/wealth-service/api/v1/customers/users";
	public static final String LOGIN = "/login";
	public static final String REGISTER = "/register";
	public static final String ME = "/me";
	public static final String LOGOUT = "/logout";
	public static final String PASSWORD_RESET = "/password/reset";

	public static final String ROLE_ADMIN = "admin";
	public static final String SESSION_VERSION_CLAIM = "sv";

	// Same limits and window as the Node login: 5 per email, 20 per IP, 15 minutes.
	public static final int LOGIN_EMAIL_LIMIT = 5;
	public static final int LOGIN_IP_LIMIT = 20;
	public static final int AUTH_IP_LIMIT = 20;
	public static final long AUTH_WINDOW_SECONDS = 900;
	public static final int EMAIL_MAX_LENGTH = 254;
	public static final int PASSWORD_MIN_LENGTH = 10;
	public static final int PASSWORD_MAX_LENGTH = 200;
}
