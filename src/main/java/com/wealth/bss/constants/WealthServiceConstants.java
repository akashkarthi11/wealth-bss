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

	// Values of wealth.actor (audit log and the external-balance guard, migrations 0008 and 0012).
	public static final String ACTOR_OWNER = "owner";
	public static final String ACTOR_AGENT = "agent";
	public static final String ACTOR_CRON = "cron";

	public static final int BALANCE_HISTORY_DEFAULT = 30;

	// Admin module paths.
	public static final String ADMIN_BASE = "/wealth-service/api/v1/admin";
	public static final String ROLES_LIST = "/roles/list";
	public static final String ROLES_UPDATE_MENUS = "/roles/update-menus";
	public static final String RECORDS_LIST = "/records/list";

	public static final String ENVIRONMENT_BASE = "/wealth-service/api/v1/admin/environment";
	public static final String ENV_REDIS_GET = "/redis/get";
	public static final String ENV_REDIS_UPDATE = "/redis/update";
	public static final String ENV_REDIS_RESET = "/redis/reset";
	public static final String ENV_REDIS_ACTIVE = "/redis/active";
	public static final String ENV_KAFKA_GET = "/kafka/get";
	public static final String ENV_KAFKA_UPDATE = "/kafka/update";
	public static final String ENV_KAFKA_RESET = "/kafka/reset";
	public static final String ENV_KAFKA_ACTIVE = "/kafka/active";
	public static final String ENV_KAFKA_REFRESH = "/kafka/refresh-time";
	public static final String ENV_POSTGRES_GET = "/postgres/get";
	public static final String ENV_POSTGRES_UPDATE = "/postgres/update";
	public static final String ENV_POSTGRES_RESET = "/postgres/reset";
	public static final String ENV_ADDRESS_SAVE = "/address/save";
	public static final String INTERNAL_SETTINGS_BASE = "/wealth-service/api/v1/internal/settings";
	public static final String INTERNAL_PICK = "/pick";
	public static final String HEADER_SERVICE_KEY = "X-Service-Key";
	public static final String SETTING_REDIS = "redis";
	public static final String SETTING_KAFKA = "kafka";
	public static final String SETTING_UI_TO_BFF = "ui_to_bff";
	public static final String SETTING_BFF_TO_BSS = "bff_to_bss";
	public static final String STATUS_UP = "UP";
	public static final String STATUS_DOWN = "DOWN";
	public static final String STATUS_NOT_CONFIGURED = "NOT_CONFIGURED";
	public static final String STATUS_OFF = "OFF";
	public static final String LINK_LOCAL_PREFIX = "169.254.";
	public static final int KAFKA_SERVERS_MAX = 5;
	public static final int ORIGIN_MAX_LENGTH = 200;
	public static final int USERNAME_MAX_LENGTH = 100;

	public static final String ACCOUNTS_BASE = "/wealth-service/api/v1/accounts";
	public static final String ACCOUNTS_LIST = "/list";
	public static final String ACCOUNTS_DETAIL = "/detail";
	public static final String ACCOUNTS_SUBTYPES = "/subtypes";
	public static final String ACCOUNTS_SPLITS_LIST = "/splits/list";
	public static final String ACCOUNTS_REFRESH = "/refresh";
	public static final String INVESTMENTS_BASE = "/wealth-service/api/v1/investments";
	public static final String INVESTMENTS_LIST = "/list";
	public static final String INVESTMENTS_REFRESH = "/refresh";
	public static final String PEOPLE_BASE = "/wealth-service/api/v1/people";
	public static final String PEOPLE_LIST = "/list";
	public static final String PEOPLE_REFRESH = "/refresh";
	// People (money lent and split bills): states, labels and limits of the list
	public static final String PEOPLE_STATUS_OPEN = "open";
	public static final String PEOPLE_STATUS_WRITTEN_OFF = "written_off";
	public static final String PEOPLE_STATUS_ACTIVE = "active";
	public static final String PEOPLE_DIRECTION_OUT = "out";
	public static final String PEOPLE_DIRECTION_IN = "in";
	public static final String PEOPLE_ITEM_TRANSFER = "transfer";
	public static final String PEOPLE_ITEM_CARRIED = "carried";
	public static final String PEOPLE_ITEM_MANUAL = "manual";
	public static final String PEOPLE_ITEM_BILL = "bill";
	public static final String PEOPLE_LABEL_CARRIED = "Carried from round-off";
	public static final String PEOPLE_LABEL_PAID_SHARE = "Paid share of ";
	public static final String PEOPLE_LABEL_A_BILL = "a bill";
	public static final String PEOPLE_LABEL_LENT = "Lent";
	public static final String PEOPLE_LABEL_PAID_BACK = "Paid back";
	public static final String PEOPLE_LABEL_SHARE_OF = "Share of ";
	public static final String PEOPLE_NAME_YOU = "You";
	public static final String PEOPLE_SOURCE_SEPARATOR = " \u00b7 ";
	public static final String PEOPLE_RUPEE = "\u20b9";
	public static final int PEOPLE_UNLINKED_PER_PERSON_MAX = 40;
	public static final int PEOPLE_SUGGESTION_MAX = 30;
	public static final double PEOPLE_OVERPAID_LIMIT = 0.004;
	public static final double PEOPLE_DAYS_PER_YEAR = 365.0;
	public static final int MONEY_SCALE = 2;
	public static final int PRICE_SCALE = 4;

	public static final String ACCOUNT_ACTIVE = "active";
	public static final String CURRENCY_INR = "INR";
	public static final String CATEGORY_CASA = "CASA";
	public static final String CATEGORY_INVESTMENT = "INVESTMENT";
	public static final String CATEGORY_LIABILITY = "LIABILITY";
	public static final String BALANCE_PENDING_RECONCILIATION = "pending_reconciliation";
	public static final String BALANCE_ESTIMATED = "estimated";
	public static final String VALUATION_HOLDINGS = "holdings";
	public static final int BALANCE_HISTORY_ROWS = 30;


	public static final String ROLES_CREATE = "/roles/create";
	public static final String USERS_LIST = "/users/list";
	public static final String USERS_SET_ROLE = "/users/set-role";
	public static final int ROLE_LABEL_MAX_LENGTH = 40;
	public static final int ROLE_KEY_MAX_LENGTH = 30;
	public static final String STARTING_MENU = "overview";

	public static final String THIRD_PARTY_BASE = "/wealth-service/api/v1/admin/connections";
	public static final String THIRD_PARTY_LIST = "/list";
	public static final String THIRD_PARTY_SAVE = "/save";
	public static final String THIRD_PARTY_TEST = "/test";
	public static final String THIRD_PARTY_REMOVE = "/remove";
	public static final String THIRD_PARTY_FETCH = "/fetch";
	public static final String BROKER_ANGEL_ONE = "angel-one";
	public static final String BROKER_ACCOUNT_KEY = "angel-one-demat";
	public static final String BROKER_BANK_NAME = "Angel One";
	public static final String BROKER_ACCOUNT_NAME = "Demat";
	public static final String BROKER_SUBTYPE = "demat_equity";
	public static final String BROKER_PRICE_SOURCE = "angel-one";
	public static final String BROKER_LOCK = "wealth:broker-fetch:lock";
	public static final String BROKER_ZONE = "Asia/Kolkata";
	// 18:30 on weekdays, after the market has closed and the day's prices are final
	public static final String BROKER_FETCH_CRON = "0 30 18 * * MON-FRI";
	public static final int BROKER_LOCK_MINUTES = 30;
	public static final long REDIS_RETRY_MILLIS = 10_000;
	public static final int NEXUS_LOCAL_MAX_ENTRIES = 10_000;
	// Nexus read cache (rules 10): fresh under the refresh time, stale after it (a refresh is requested), gone after the expiry.
	public static final int NEXUS_REFRESH_DEFAULT_SECONDS = 10;
	public static final int NEXUS_REFRESH_MIN_SECONDS = 5;
	public static final int NEXUS_REFRESH_MAX_SECONDS = 60;
	public static final int NEXUS_READ_EXPIRY_SECONDS = 300;
	public static final int NEXUS_LOCK_SECONDS = 5;
	public static final int NEXUS_HARD_REFRESH_SECONDS = 5;
	public static final String NEXUS_KEY_PREFIX = "wealth:nexus-";
	public static final String NEXUS_LOCK_PREFIX = "wealth:nexus-lock:";
	public static final String NEXUS_HARD_PREFIX = "wealth:nexus-hard:";
	public static final String NEXUS_READ_ACCOUNTS = "accounts-overview";
	public static final String NEXUS_READ_INVESTMENTS = "investments";
	public static final String NEXUS_READ_PEOPLE = "people";
	public static final String KAFKA_TOPIC_MONEY = "wealth.money-events";
	public static final String KAFKA_HEADER_EVENT = "eventType";
	public static final String KAFKA_EVENT_CACHE_REFRESH = "cache.refresh";
	public static final String KAFKA_GROUP_NEXUS = "wealth-nexus";
	public static final int KAFKA_SEND_TIMEOUT_MS = 3000;
	public static final long KAFKA_POLL_MILLIS = 1000;
	public static final long KAFKA_RETRY_MILLIS = 10_000;
	// The pause after a failed connect doubles this many times (10 s, 20 s, 40 s) and then stays.
	public static final int KAFKA_RETRY_DOUBLINGS = 2;
	public static final long KAFKA_CONSUMER_ALIVE_MILLIS = 15_000;
	public static final String INSTRUMENT_CLASS_INDIAN_EQUITY = "indian_equity";
	public static final String INSTRUMENT_UNIT_SHARE = "share";
	public static final String PRICE_SOURCE_YAHOO = "yahoo";
	public static final String YAHOO_SUFFIX_NSE = ".NS";
	public static final String YAHOO_SUFFIX_BSE = ".BO";
	public static final String EXCHANGE_BSE = "BSE";
	public static final String BROKER_EQUITY_SUFFIX = "-EQ";
	public static final String API_USAGE_BASE = "/wealth-service/api/v1/advisor/api-usage";
	public static final String API_USAGE_LIST = "/list";
	// the four kinds of API usage: our BSS APIs, our BFF APIs (reported by the BFF), the third-party APIs we call, and the scheduled jobs
	public static final String USAGE_BSS = "bss";
	public static final String USAGE_BFF = "bff";
	public static final String USAGE_EXTERNAL = "external";
	public static final String USAGE_JOB = "job";
	public static final String INTERNAL_USAGE_BASE = "/wealth-service/api/v1/internal/usage";
	public static final String INTERNAL_USAGE_RECORD = "/record";
	public static final int USAGE_BATCH_MAX = 500;
	public static final int USAGE_NAME_MAX = 200;
	public static final long USAGE_COUNT_MAX = 1_000_000_000L;
	public static final String USAGE_JOB_BROKER_FETCH = "broker-fetch";
	public static final String USAGE_UNMATCHED = "UNMATCHED";
	// usage by user: "-" in memory and Redis, the all-zero id in the table, means no user; the request value "me" is the caller
	public static final String USAGE_NO_USER = "-";
	public static final String USAGE_NO_USER_ID = "00000000-0000-0000-0000-000000000000";
	public static final String USAGE_USER_ME = "me";
	public static final String USAGE_KEY_PREFIX = "wealth:usage:";
	public static final String USAGE_PENDING_KEY = "wealth:usage:pending";
	public static final int USAGE_DAYS_DEFAULT = 7;
	public static final int USAGE_DAYS_MAX = 30;
	public static final int USAGE_KEEP_DAYS = 8;
	public static final int USAGE_BUFFER_MAX = 50_000;
	// every 5 seconds the counts held in memory are added to Redis; every 5 minutes they are saved to the database
	public static final long USAGE_DRAIN_MILLIS = 5_000;
	public static final String USAGE_SAVE_CRON = "0 */5 * * * *";
	public static final int FETCH_NOTE_MAX_LENGTH = 200;
	public static final int EXTRA_VALUE_MAX_LENGTH = 100;
	public static final int PROVIDER_LABEL_MAX_LENGTH = 60;
	public static final int PROVIDER_PRIORITY_MAX = 99;
	public static final int PROVIDER_KEY_MAX_LENGTH = 500;
	public static final int KEY_HINT_LENGTH = 4;

	public static final String MENUS_BASE = "/wealth-service/api/v1/menus";
	public static final String MENUS_LIST = "/list";

	public static final String ROLE_USER = "user";
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
