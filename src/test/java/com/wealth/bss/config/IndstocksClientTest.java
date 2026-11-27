package com.wealth.bss.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.anyLong;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;
import com.wealth.bss.nexus.service.IfApiUsageService;

// The INDstocks call is checked against a local server; nothing here reaches the real one.
class IndstocksClientTest {

	private HttpServer server;
	private String base;
	private final AtomicReference<String> auth = new AtomicReference<>();
	private final AtomicReference<String> path = new AtomicReference<>();
	private volatile int code = 200;

	@BeforeEach
	void start() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", exchange -> {
			auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
			path.set(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
			exchange.sendResponseHeaders(code, -1);
			exchange.close();
		});
		server.start();
		base = "http://127.0.0.1:" + server.getAddress().getPort();
	}

	@AfterEach
	void stop() {
		server.stop(0);
	}

	@Test
	void theTokenIsSentRawInTheAuthorizationHeaderToTheHoldingsPathAndTheStatusIsReturned() {
		IfApiUsageService usage = mock(IfApiUsageService.class);
		IndstocksClient client = new IndstocksClient(usage);
		assertEquals(200, client.probe(base + "/", "tok-123"));
		assertEquals("tok-123", auth.get(), "no Bearer prefix");
		assertEquals("GET /portfolio/holdings", path.get());
		verify(usage).recordExternal(eq("indstocks"), eq("holdings-check"), eq(true), anyLong());

		code = 401;
		assertEquals(401, client.probe(base, "tok-123"));
		verify(usage).recordExternal(eq("indstocks"), eq("holdings-check"), eq(false), anyLong());
		code = 429;
		assertEquals(429, client.probe(base, "tok-123"));
	}

	@Test
	void aTokenIsNeverSentToAnAddressThatIsNotHttpsOrLocalOrToAnAddressThatIsDown() {
		IndstocksClient client = new IndstocksClient();
		assertThrows(IllegalStateException.class, () -> client.probe("http://example.com", "tok"));
		assertThrows(IllegalStateException.class, () -> client.probe("ftp://example.com", "tok"));
		assertThrows(IllegalStateException.class, () -> client.probe(null, "tok"));
		assertThrows(IllegalStateException.class, () -> client.probe(base, " "));
		assertThrows(IllegalStateException.class, () -> client.probe(base, null));
		server.stop(0);
		IllegalStateException down = assertThrows(IllegalStateException.class, () -> client.probe(base, "secret-token-value"));
		assertEquals("INDstocks could not be reached", down.getMessage(), "the message never carries the token");
	}
}
