package com.wealth.bss.config;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wealth.bss.nexus.service.IfApiUsageService;
import com.wealth.common.logger.KarthiLogger;

// Angel One SmartAPI, read only: sign in (client code, PIN and a one-time code made from the TOTP secret) and read the holdings. The same two calls the old
// app made. Failures carry a short reason that never includes a secret; nothing from the answer is logged.
@Component
public class AngelOneClient {

	private static final KarthiLogger LOGGER = new KarthiLogger(AngelOneClient.class);
	private static final String PROVIDER = "angel-one";
	private static final String LOGIN = "/rest/auth/angelbroking/user/v1/loginByPassword";
	private static final String HOLDINGS = "/rest/secure/angelbroking/portfolio/v1/getAllHolding";
	private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
	private static final int SECONDS = 15;
	private static final int TOTP_STEP_SECONDS = 30;
	private static final int TOTP_DIGITS = 6;

	// The values Angel One needs to sign in.
	public record Credentials(String apiKey, String clientCode, String pin, String totpSecret, String macAddress) {
		public boolean complete() {
			return filled(apiKey) && filled(clientCode) && filled(pin) && filled(totpSecret) && filled(macAddress);
		}

		private static boolean filled(String value) {
			return value != null && !value.isBlank();
		}
	}

	// One holding as Angel One reports it.
	public record Holding(String symbol, String isin, String exchange, String token, BigDecimal quantity, BigDecimal averagePrice, BigDecimal lastPrice, String product,
			BigDecimal t1Quantity, BigDecimal collateralQuantity, BigDecimal haircut, BigDecimal close, BigDecimal profitAndLoss, BigDecimal pnlPercent) {
	}

	// The login values kept together as one encrypted block, by these names.
	public static final List<String> EXTRA_KEYS = List.of("clientCode", "pin", "totpSecret", "macAddress");

	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(SECONDS)).followRedirects(HttpClient.Redirect.NEVER).build();
	private final ObjectMapper mapper = new ObjectMapper();
	// Counts every call to Angel One (rules: API usage). Null in a test that does not need counting.
	private final IfApiUsageService usage;

	public AngelOneClient() {
		this(null);
	}

	@Autowired
	public AngelOneClient(IfApiUsageService usage) {
		this.usage = usage;
	}

	// The block of login values (JSON of EXTRA_KEYS) as a map; empty when there is none or it cannot be read.
	public Map<String, String> readExtra(String json) {
		if (json == null || json.isBlank()) {
			return new LinkedHashMap<>();
		}
		try {
			return mapper.readValue(json, new TypeReference<LinkedHashMap<String, String>>() {
			});
		} catch (IOException e) {
			return new LinkedHashMap<>();
		}
	}

	public String writeExtra(Map<String, String> values) {
		try {
			return mapper.writeValueAsString(values);
		} catch (IOException e) {
			throw new IllegalStateException("The login values could not be saved");
		}
	}

	public Credentials credentials(String apiKey, Map<String, String> extra) {
		return new Credentials(apiKey, extra.get("clientCode"), extra.get("pin"), extra.get("totpSecret"), extra.get("macAddress"));
	}

	// Signs in; the access token, or IllegalStateException with a short reason.
	public String login(String baseUrl, Credentials credentials) {
		try {
			String body = mapper.writeValueAsString(java.util.Map.of("clientcode", credentials.clientCode(), "password", credentials.pin(), "totp",
					totp(credentials.totpSecret(), System.currentTimeMillis() / 1000)));
			JsonNode answer = send("login", request(baseUrl + LOGIN, credentials).POST(HttpRequest.BodyPublishers.ofString(body)).build());
			String token = answer.path("data").path("jwtToken").asText("");
			if (token.isEmpty()) {
				throw new IllegalStateException("Angel One sign-in did not return an access token");
			}
			return token;
		} catch (IOException e) {
			throw new IllegalStateException("Angel One could not be reached");
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Angel One call was interrupted");
		}
	}

	// Signs in and reads the holdings.
	public List<Holding> holdings(String baseUrl, Credentials credentials) {
		String token = login(baseUrl, credentials);
		try {
			JsonNode answer = send("holdings", request(baseUrl + HOLDINGS, credentials).header("Authorization", "Bearer " + token).GET().build());
			JsonNode list = answer.path("data").path("holdings");
			if (!list.isArray()) {
				throw new IllegalStateException("Angel One did not return a holdings list");
			}
			List<Holding> result = new ArrayList<>();
			for (JsonNode item : list) {
				String symbol = item.path("tradingsymbol").asText("").trim();
				if (symbol.isEmpty()) {
					continue;
				}
				result.add(new Holding(symbol, item.path("isin").asText(""), item.path("exchange").asText(""), item.path("symboltoken").asText(""),
						decimal(item, "quantity"), decimal(item, "averageprice"), decimal(item, "ltp"), item.path("product").asText("").trim(), optional(item, "t1quantity"),
						optional(item, "collateralquantity"), optional(item, "haircut"), optional(item, "close"), optional(item, "profitandloss"), optional(item, "pnlpercentage")));
			}
			return result;
		} catch (IOException e) {
			throw new IllegalStateException("Angel One could not be reached");
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Angel One call was interrupted");
		}
	}

	private HttpRequest.Builder request(String url, Credentials credentials) {
		return HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(SECONDS)).header("Content-Type", "application/json").header("Accept", "application/json")
				.header("X-PrivateKey", credentials.apiKey()).header("X-MACAddress", credentials.macAddress()).header("X-UserType", "USER").header("X-SourceID", "WEB");
	}

	// One call to Angel One, counted once with its result and time, whatever the answer. endpoint is a short name (login, holdings), never the address.
	private JsonNode send(String endpoint, HttpRequest request) throws IOException, InterruptedException {
		long started = System.nanoTime();
		boolean worked = false;
		try {
			JsonNode answer = exchange(request);
			worked = true;
			return answer;
		} finally {
			if (usage != null) {
				usage.recordExternal(PROVIDER, endpoint, worked, (System.nanoTime() - started) / 1_000_000L);
			}
		}
	}

	// The answer as JSON; a refusal (HTTP error, or status false) is an IllegalStateException with Angel One's own short message.
	private JsonNode exchange(HttpRequest request) throws IOException, InterruptedException {
		HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
		JsonNode body;
		try {
			body = mapper.readTree(response.body());
		} catch (IOException e) {
			body = mapper.createObjectNode();
		}
		boolean refused = response.statusCode() < 200 || response.statusCode() >= 300 || body.path("status").asText("true").equalsIgnoreCase("false");
		if (refused) {
			String message = body.path("message").asText("");
			LOGGER.warn("Angel One refused a request: HTTP " + response.statusCode());
			throw new IllegalStateException(message.isEmpty() ? "Angel One refused the request (HTTP " + response.statusCode() + ")" : shorten(message));
		}
		return body;
	}

	private static String shorten(String message) {
		String clean = message.replaceAll("\\p{Cntrl}", " ").trim();
		return clean.length() > 120 ? clean.substring(0, 120) : clean;
	}

	// A number Angel One may leave out: null when it is missing, empty or not a number.
	private static BigDecimal optional(JsonNode item, String field) {
		JsonNode value = item.path(field);
		if (value.isMissingNode() || value.isNull() || value.asText("").isBlank()) {
			return null;
		}
		try {
			return new BigDecimal(value.asText().trim());
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static BigDecimal decimal(JsonNode item, String field) {
		try {
			return new BigDecimal(item.path(field).asText("0").trim());
		} catch (NumberFormatException e) {
			return BigDecimal.ZERO;
		}
	}

	// The 6-digit one-time code for this moment (RFC 6238: HMAC-SHA1, 30-second steps) from a base32 TOTP secret.
	public static String totp(String base32Secret, long epochSeconds) {
		byte[] key = decodeBase32(base32Secret);
		byte[] counter = ByteBuffer.allocate(8).putLong(epochSeconds / TOTP_STEP_SECONDS).array();
		try {
			Mac mac = Mac.getInstance("HmacSHA1");
			mac.init(new SecretKeySpec(key, "HmacSHA1"));
			byte[] hash = mac.doFinal(counter);
			int offset = hash[hash.length - 1] & 0x0f;
			int binary = ((hash[offset] & 0x7f) << 24) | ((hash[offset + 1] & 0xff) << 16) | ((hash[offset + 2] & 0xff) << 8) | (hash[offset + 3] & 0xff);
			int modulo = 1;
			for (int i = 0; i < TOTP_DIGITS; i++) {
				modulo *= 10;
			}
			return String.format(Locale.ROOT, "%0" + TOTP_DIGITS + "d", binary % modulo);
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("The one-time code could not be made");
		}
	}

	private static byte[] decodeBase32(String secret) {
		String clean = secret.toUpperCase(Locale.ROOT).replaceAll("[=\\s]", "");
		java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
		int buffer = 0;
		int bits = 0;
		for (char c : clean.toCharArray()) {
			int value = BASE32.indexOf(c);
			if (value < 0) {
				throw new IllegalStateException("The TOTP secret is not valid base32");
			}
			buffer = (buffer << 5) | value;
			bits += 5;
			if (bits >= 8) {
				out.write((buffer >> (bits - 8)) & 0xff);
				bits -= 8;
			}
		}
		return out.toByteArray();
	}
}
