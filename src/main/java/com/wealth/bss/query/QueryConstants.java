package com.wealth.bss.query;

public final class QueryConstants {

	private QueryConstants() {
	}

	private static final String WEALTH_USERS = "WEALTH_USERS";
	private static final String WEALTH_ROLE_MENUS = "WEALTH_ROLE_MENUS";

	// password_hash is never selected here
	public static final String FIND_WEALTH_USER_BY_ID = "SELECT ID, EMAIL, ROLE, SESSION_VERSION, CREATED_AT FROM " + WEALTH_USERS + " WHERE ID = CAST(? AS UUID)";

	public static final String FIND_CREDENTIAL_BY_EMAIL = "SELECT ID, EMAIL, PASSWORD_HASH, ROLE, SESSION_VERSION FROM " + WEALTH_USERS + " WHERE EMAIL = ?";

	public static final String INSERT_WEALTH_USER = "INSERT INTO " + WEALTH_USERS + " (EMAIL, PASSWORD_HASH) VALUES (?, ?) ON CONFLICT (EMAIL) DO NOTHING RETURNING ID, EMAIL, ROLE, SESSION_VERSION, CREATED_AT";

	// a new hash bumps SESSION_VERSION, which signs out every older token
	public static final String UPDATE_WEALTH_USER_PASSWORD = "UPDATE " + WEALTH_USERS + " SET PASSWORD_HASH = ?, SESSION_VERSION = SESSION_VERSION + 1 WHERE EMAIL = ? RETURNING ID, EMAIL, ROLE, SESSION_VERSION, CREATED_AT";

	public static final String FIND_MENUS_BY_ROLE = "SELECT ARRAY_TO_STRING(MENUS, ',') FROM " + WEALTH_ROLE_MENUS + " WHERE ROLE = ?";
}
