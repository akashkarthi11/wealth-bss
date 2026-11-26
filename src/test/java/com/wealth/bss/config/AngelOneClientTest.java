package com.wealth.bss.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;
import com.wealth.bss.config.AngelOneClient.Credentials;
import com.wealth.bss.config.AngelOneClient.Holding;

// The sign-in code is checked against the RFC 6238 test values, and the two calls against a local server that answers like Angel One.
class AngelOneClientTest {

	// RFC 6238's secret "12345678901234567890" in base32.
	private static final String RFC_SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

	private HttpServer server;
	private String base;
	private final AtomicReference<String> loginBody = new AtomicReference<>();
	private final AtomicReference<String> privateKey = new AtomicReference<>();
	private final AtomicReference<String> holdingsAuth = new AtomicReference<>();
	private volatile String loginAnswer = "{\"status\":true,\"data\":{\"jwtToken\":\"the-jwt\"}}";
	private volatile int loginCode = 200;
	private volatile String holdingsAnswer = "{\"status\":true,\"data\":{\"holdings\":[]}}";

	@BeforeEach
	void start() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/rest/auth/angelbroking/user/v1/loginByPassword", exchange -> {
			loginBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			privateKey.set(exchange.getRequestHeaders().getFirst("X-PrivateKey"));
			reply(exchange, loginCode, loginAnswer);
		});
		server.createContext("/rest/secure/angelbroking/portfolio/v1/getAllHolding", exchange -> {
			holdingsAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
			reply(exchange, 200, holdingsAnswer);
		});
		server.start();
		base = "http://127.0.0.1:" + server.getAddress().getPort();
	}

	private void reply(com.sun.net.httpserver.HttpExchange exchange, int code, String body) throws IOException {
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
		exchange.sendResponseHeaders(code, bytes.length);
		exchange.getResponseBody().write(bytes);
		exchange.close();
	}

	@AfterEach
	void stop() {
		server.stop(0);
	}

	private final AngelOneClient client = new AngelOneClient();
	private final Credentials credentials = new Credentials("api-key", "A123", "1234", RFC_SECRET, "00:11:22:33:44:55");

	@Test
	void theOneTimeCodeMatchesTheRfc6238TestValues() {
		assertEquals("287082", AngelOneClient.totp(RFC_SECRET, 59));
		assertEquals("081804", AngelOneClient.totp(RFC_SECRET, 1111111109));
		assertEquals("050471", AngelOneClient.totp(RFC_SECRET, 1111111111));
		assertEquals("005924", AngelOneClient.totp(RFC_SECRET, 1234567890));
		assertEquals("287082", AngelOneClient.totp("gezd gnbv gy3t qojq gezd gnbv gy3t qojq====", 59), "case, spaces and padding do not matter");
		assertThrows(IllegalStateException.class, () -> AngelOneClient.totp("not base32 !", 59));
	}

	@Test
	void signInSendsTheClientCodeThePinAndACodeWithTheKeyInTheHeader() {
		String token = client.login(base, credentials);

		assertEquals("the-jwt", token);
		assertEquals("api-key", privateKey.get());
		assertTrue(loginBody.get().contains("\"clientcode\":\"A123\"") && loginBody.get().contains("\"password\":\"1234\""));
		assertTrue(loginBody.get().matches(".*\"totp\":\"\\d{6}\".*"));
	}

	@Test
	void holdingsAreReadWithTheAccessTokenAndMappedWithNumbers() {
		holdingsAnswer = "{\"status\":true,\"data\":{\"holdings\":[{\"tradingsymbol\":\"RELIANCE-EQ\",\"exchange\":\"NSE\",\"isin\":\"INE002A01018\",\"symboltoken\":\"2885\","
				+ "\"quantity\":10,\"averageprice\":2450.55,\"ltp\":2512.3},{\"tradingsymbol\":\"\",\"quantity\":1},{\"tradingsymbol\":\"TCS-EQ\",\"quantity\":\"3\","
				+ "\"averageprice\":\"3500\",\"ltp\":null}]}}";

		List<Holding> holdings = client.holdings(base, credentials);

		assertEquals("Bearer the-jwt", holdingsAuth.get());
		assertEquals(2, holdings.size(), "a row without a symbol is skipped");
		assertEquals("RELIANCE-EQ", holdings.get(0).symbol());
		assertEquals(0, new java.math.BigDecimal("10").compareTo(holdings.get(0).quantity()));
		assertEquals(0, new java.math.BigDecimal("2450.55").compareTo(holdings.get(0).averagePrice()));
		assertEquals("INE002A01018", holdings.get(0).isin());
		assertEquals(0, java.math.BigDecimal.ZERO.compareTo(holdings.get(1).lastPrice()), "a missing price is zero");
	}

	@Test
	void anEmptyListIsFineButAMissingListOrARefusalIsAFailureWithAShortReason() {
		assertTrue(client.holdings(base, credentials).isEmpty());

		holdingsAnswer = "{\"status\":true,\"data\":{}}";
		assertEquals("Angel One did not return a holdings list", assertThrows(IllegalStateException.class, () -> client.holdings(base, credentials)).getMessage());

		loginAnswer = "{\"status\":false,\"message\":\"Invalid totp\",\"errorcode\":\"AB1050\"}";
		assertEquals("Invalid totp", assertThrows(IllegalStateException.class, () -> client.login(base, credentials)).getMessage());

		loginAnswer = "not json";
		loginCode = 500;
		assertTrue(assertThrows(IllegalStateException.class, () -> client.login(base, credentials)).getMessage().contains("HTTP 500"));

		loginCode = 200;
		loginAnswer = "{\"status\":true,\"data\":{}}";
		assertEquals("Angel One sign-in did not return an access token", assertThrows(IllegalStateException.class, () -> client.login(base, credentials)).getMessage());
	}

	@Test
	void aFailureNeverCarriesTheSecrets() {
		loginAnswer = "{\"status\":false,\"message\":\"Bad request\"}";
		String message = assertThrows(IllegalStateException.class, () -> client.login(base, credentials)).getMessage();
		for (String secret : new String[] { "api-key", "1234", RFC_SECRET, "A123" }) {
			assertFalse(message.contains(secret), secret);
		}
		assertThrows(IllegalStateException.class, () -> client.login("http://127.0.0.1:9", credentials));
	}

	@Test
	void incompleteCredentialsAreRecognised() {
		assertTrue(credentials.complete());
		assertFalse(new Credentials("k", "c", "", "s", "m").complete());
		assertFalse(new Credentials(null, "c", "p", "s", "m").complete());
		assertFalse(new Credentials("k", "c", "p", " ", "m").complete());
	}
}
