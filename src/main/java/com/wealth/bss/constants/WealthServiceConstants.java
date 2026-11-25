package com.wealth.bss.constants;

public final class WealthServiceConstants {

	private WealthServiceConstants() {
	}

	public static final String ENTERING = "Entering ";
	public static final String EXITING = "Exiting ";

	public static final String REQUEST_CONTEXT_KEY_PREFIX = "requestContext:";

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
