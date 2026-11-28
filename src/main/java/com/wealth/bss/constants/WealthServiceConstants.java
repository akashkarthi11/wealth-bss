package com.wealth.bss.constants;

import java.util.List;

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
	public static final String ENV_SIGNUP_GET = "/signup/get";
	public static final String ENV_SIGNUP_UPDATE = "/signup/update";
	public static final String ENV_SIGNUP_CLOSE = "/signup/close";
	public static final String ENV_SIGNUP_RESET = "/signup/reset";
	public static final String INTERNAL_SETTINGS_BASE = "/wealth-service/api/v1/internal/settings";
	public static final String INTERNAL_PICK = "/pick";
	public static final String HEADER_SERVICE_KEY = "X-Service-Key";
	public static final String SETTING_REDIS = "redis";
	public static final String SETTING_KAFKA = "kafka";
	public static final String SETTING_UI_TO_BFF = "ui_to_bff";
	public static final String SETTING_BFF_TO_BSS = "bff_to_bss";
	// Sign-up access code: saved = the admin's code, closed = no one can sign up, deployed = the WEALTH_ACCESS_KEY of the server.
	public static final String SETTING_SIGNUP_CODE = "signup_code";
	public static final String SIGNUP_SAVED = "saved";
	public static final String SIGNUP_CLOSED = "closed";
	public static final String SIGNUP_DEPLOYED = "deployed";
	public static final int ACCESS_CODE_MIN_LENGTH = 10;
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
	public static final String ACCOUNTS_CREATE = "/create";
	public static final String ACCOUNTS_BALANCE_RECORD = "/balances/record";
	public static final String ACCOUNTS_FD_TERMS_UPDATE = "/fd-terms/update";
	public static final String VALUATION_BALANCE = "balance";
	public static final String BALANCE_VERIFIED = "verified";
	public static final String BALANCE_REPORTED = "reported";
	public static final String BALANCE_STALE = "stale";
	public static final double BALANCE_ABS_MAX = 1e12;
	public static final int BALANCE_NOTE_MAX_LENGTH = 300;
	public static final String BALANCE_ALREADY_STORED = "already_stored";
	public static final List<String> SUBTYPES_WITH_ACCOUNT_COPY = List.of("savings", "current");
	// adding an account: limits, currencies, the fixed-deposit type and its allowed payout and compounding
	public static final int ACCOUNT_TEXT_MAX_LENGTH = 80;
	public static final int ACCOUNT_MASK_MAX_LENGTH = 20;
	public static final String CURRENCY_USD = "USD";
	public static final String SUBTYPE_FIXED_DEPOSIT = "fixed_deposit";
	public static final String SUBTYPE_US_BROKERAGE = "us_brokerage";
	public static final List<String> FD_PAYOUTS = List.of("cumulative", "monthly", "quarterly", "yearly");
	public static final List<String> FD_COMPOUNDING = List.of("simple", "monthly", "quarterly", "half_yearly", "yearly");
	public static final String INVESTMENTS_BASE = "/wealth-service/api/v1/investments";
	public static final String INVESTMENTS_LIST = "/list";
	public static final String INVESTMENTS_REFRESH = "/refresh";
	public static final String TRANSACTIONS_BASE = "/wealth-service/api/v1/transactions";
	public static final String TRANSACTIONS_LIST = "/list";
	public static final String TRANSACTIONS_SUMMARY = "/summary";
	public static final String TRANSACTIONS_REFRESH = "/refresh";
	public static final String TRANSACTIONS_CREATE = "/create";
	public static final String TRANSACTIONS_DELETE = "/delete";
	public static final String TRANSACTIONS_WORKFLOW_LIST = "/workflow/list";
	public static final String TRANSACTIONS_WORKFLOW_DECIDE = "/workflow/decide";
	// statement lines: where a transaction came from, what it is called when nothing better is known, and how alike two descriptions must be to be the same payment
	public static final String STATEMENT_REF_PREFIX = "stmt:";
	public static final String STATEMENT_CATEGORY = "Bank statement";
	public static final String STATEMENT_KIND_DEBIT = "expense";
	public static final String STATEMENT_KIND_INCOME = "income";
	public static final String DIRECTION_DEBIT = "debit";
	public static final String DIRECTION_CREDIT = "credit";
	public static final String WORKFLOW_POSSIBLE_DUPLICATE = "possible_duplicate";
	public static final String WORKFLOW_PARSE_UNSURE = "parse_unsure";
	public static final String WORKFLOW_SUSPICIOUS = "suspicious";
	public static final String WORKFLOW_SOURCE_ALERT = "alert";
	public static final String WORKFLOW_SOURCE_STATEMENT = "statement";
	public static final String REVIEW_ACCEPTED = "accepted";
	public static final String WORKFLOW_OPEN = "open";
	public static final String WORKFLOW_MERGED = "merged";
	public static final String WORKFLOW_ACCEPTED = "accepted";
	public static final String WORKFLOW_DISMISSED = "dismissed";
	public static final String DECISION_MERGE = "merge";
	public static final String DECISION_ACCEPT = "accept";
	public static final String DECISION_DISMISS = "dismiss";
	public static final double STATEMENT_SIMILARITY_MIN = 0.2;
	public static final int STATEMENT_TOKEN_MIN_LENGTH = 3;
	public static final int STATEMENT_DESCRIPTION_MAX_LENGTH = 300;
	public static final int STATEMENT_REF_MAX_LENGTH = 200;
	public static final int STATEMENT_DIGITS_MAX_LENGTH = 40;
	public static final String NEXUS_READ_TRANSACTIONS = "transactions";
	public static final String TRANSACTION_UNASSIGNED = "Unassigned";
	public static final int TRANSACTIONS_PAGE_DEFAULT = 50;
	public static final int TRANSACTIONS_PAGE_MAX = 200;
	public static final int TRANSACTION_SEARCH_MAX_LENGTH = 100;
	public static final int TRANSACTION_CATEGORY_MAX_LENGTH = 60;
	public static final int TRANSACTION_DESCRIPTION_MAX_LENGTH = 180;
	public static final int TRANSACTION_REFERENCE_MAX_LENGTH = 120;
	public static final double TRANSACTION_AMOUNT_MAX = 1e12;
	public static final String DAY_PATTERN = "^[0-9]{4}-[0-9]{2}-[0-9]{2}$";
	public static final List<String> TRANSACTION_KINDS = List.of("income", "expense", "transfer", "investment", "liability");
	public static final String INVESTMENTS_HOLDING_SAVE = "/holdings/save";
	public static final String PRICE_SOURCE_MANUAL = "manual";
	public static final String BROKER_CASH_NOTE = "Angel One funds: available cash (may include margin)";
	public static final String UUID_PATTERN = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";
	public static final int HOLDING_NAME_MAX_LENGTH = 80;
	public static final int HOLDING_SYMBOL_MAX_LENGTH = 40;
	public static final double HOLDING_QUANTITY_MAX = 1e12;
	public static final List<String> HOLDING_UNITS = List.of("share", "unit", "g");
	// the asset classes an account type may hold
	public static final java.util.Map<String, List<String>> HOLDING_ASSET_CLASSES = java.util.Map.of("demat_equity", List.of("indian_equity"), "mutual_fund", List.of("indian_mutual_fund"),
			"us_brokerage", List.of("us_equity", "us_etf"), "digital_gold", List.of("precious_metal"), "digital_silver", List.of("precious_metal"));
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
	public static final String ACCOUNT_CLOSED = "closed";
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
	public static final String THIRD_PARTY_OVERVIEW = "/overview";
	public static final String BROKER_ANGEL_ONE = "angel-one";
	public static final String GMAIL_BASE = "/wealth-service/api/v1/gmail";
	public static final String GMAIL_STATUS = "/status";
	public static final String GMAIL_CONNECT_URL = "/connect-url";
	public static final String GMAIL_CONNECT_FINISH = "/connect-finish";
	public static final String GMAIL_TEST = "/test";
	public static final String GMAIL_DISCONNECT = "/disconnect";
	public static final String GMAIL_APP_SAVE = "/app-save";
	public static final String GMAIL_APP_REMOVE = "/app-remove";
	public static final String GMAIL_STATEMENT_SAVE = "/statement-save";
	public static final String GMAIL_STATEMENT_CLEAR = "/statement-clear";
	public static final String GMAIL_STATEMENT_REVEAL = "/statement-reveal";
	public static final String GMAIL_STATEMENTS = "/statements";
	public static final String GMAIL_STATEMENT_LIST = "/statement-list";
	public static final String GMAIL_STATEMENT_ACCEPT = "/statement-accept";
	public static final String GMAIL_STATEMENT_HISTORY = "/statement-history";
	public static final String GMAIL_STATEMENT_UPLOAD = "/statement-upload";
	public static final String GMAIL_STATEMENT_RETRY = "/statement-retry";
	public static final String GMAIL_STATEMENT_DOWNLOAD = "/statement-download";
	public static final String BROKER_BASE = "/wealth-service/api/v1/broker";
	public static final String BROKER_STATUS = "/status";
	public static final String BROKER_SAVE = "/save";
	public static final String BROKER_TEST = "/test";
	public static final String BROKER_FETCH = "/fetch";
	public static final String BROKER_REMOVE = "/remove";
	public static final int BROKER_VALUE_MAX = 200;
	public static final String INDSTOCKS = "indstocks";
	public static final String INDSTOCKS_BASE = "/wealth-service/api/v1/indstocks";
	public static final String INDSTOCKS_PROBE_PATH = "/portfolio/holdings";
	public static final int INDSTOCKS_TOKEN_MAX = 500;
	public static final String GEMINI_PROVIDER = "gemini";
	public static final String GEMINI_MODEL = "gemini-2.5-flash";
	public static final int AI_TEXT_MAX = 60_000;
	public static final int AI_ROWS_MAX = 500;
	public static final int AI_STATEMENTS_PER_READ = 6;
	public static final String STATEMENT_OPENED = "opened";
	public static final String STATEMENT_LOCKED = "locked";
	public static final String STATEMENT_FAILED = "failed";
	public static final String STATEMENT_ORIGIN_GMAIL = "gmail";
	public static final String STATEMENT_ORIGIN_UPLOAD = "upload";
	public static final String STATEMENT_UPLOAD_REF = "upload:";
	public static final int STATEMENT_FILE_NAME_MAX = 200;
	public static final int STATEMENT_PASSWORD_MAX = 100;
	// Statement PDFs from these senders (never a duplicate copy); {days} is how far back, set in Connections > Common. Opened in memory only.
	public static final String GMAIL_STATEMENT_QUERY = "has:attachment filename:pdf subject:statement -subject:duplicate newer_than:{days}d (from:noreply@slice.bank.in "
			+ "OR from:roar@unitybank.co.in OR from:customercommunication@unitybank.co.in OR from:Emailstatements.cards@hdfcbank.bank.in "
			+ "OR from:noreply-estatement@alerts.bankofindia.bank.in)";
	public static final String GMAIL_DAYS_TOKEN = "{days}";
	// Defaults and limits for the three numbers: statement days and mails a read, and alert days.
	public static final int GMAIL_STATEMENT_DAYS_DEFAULT = 180;
	public static final int GMAIL_STATEMENT_MAX_DEFAULT = 40;
	public static final int GMAIL_ALERT_DAYS_DEFAULT = 35;
	public static final int GMAIL_DAYS_MIN = 7;
	public static final int GMAIL_DAYS_MAX = 730;
	public static final int GMAIL_MAILS_MIN = 1;
	public static final int GMAIL_MAILS_MAX = 100;
	public static final String GMAIL_FETCH = "/fetch";
	public static final String GMAIL_PENDING = "/pending";
	public static final String GMAIL_REVIEW = "/review";
	// Only the transaction alerts are searched (never OTP or service mails), from the last {days} days (set in Connections > Common), at most 100 a read. A mail is read once: its id is the transaction's external_ref.
	public static final String GMAIL_ALERT_QUERY = "((from:alerts@hdfcbank.bank.in subject:(debited OR \"UPI txn\" OR \"payment was made\")) "
			+ "OR (from:noreply@slice.bank.in subject:(debited OR received))) newer_than:{days}d";
	public static final int GMAIL_FETCH_MAX = 100;
	public static final String GMAIL_REF_PREFIX = "gmail:";
	public static final String GMAIL_CATEGORY = "Bank alert";
	public static final String GMAIL_ACCEPT = "accept";
	public static final String GMAIL_DISMISS = "dismiss";
	// gmail: the Google client in the main and common tables. gmail-user: each user's mailbox token in the user table.
	public static final String GMAIL_APP = "gmail";
	public static final String GMAIL_USER = "gmail-user";
	public static final String GMAIL_CALLBACK_PATH = "/my-connections/gmail/callback";
	public static final int GMAIL_VALUE_MAX_LENGTH = 200;
	public static final int GMAIL_REDIRECT_MAX_LENGTH = 300;
	public static final int GMAIL_CODE_MAX_LENGTH = 2000;
	public static final long GMAIL_STATE_MINUTES = 10;
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

	public static final String FINVU_AA_BASE = "/wealth-service/api/v1/aa/finvu";
	public static final String FINVU_AA_CONFIG = "/config";
	public static final String FINVU_AA_CONSENT_REQUEST = "/consent/request";
	public static final String FINVU_AA_CONSENT_STATUS = "/consent/status";
	public static final String FINVU_AA_FI_REQUEST = "/fi/request";
	public static final String FINVU_AA_FI_STATUS = "/fi/status";
	public static final String FINVU_AA_FI_FETCH = "/fi/fetch";

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
