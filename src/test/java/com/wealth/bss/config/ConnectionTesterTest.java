package com.wealth.bss.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

// Runs the real tester against a small local server, so the request each provider gets is checked without any internet.
class ConnectionTesterTest {

	private HttpServer server;
	private String base;
	private final AtomicReference<String> seenPath = new AtomicReference<>();
	private final AtomicReference<String> seenAuth = new AtomicReference<>();
	private final AtomicReference<String> seenApiKey = new AtomicReference<>();
	private volatile int answer = 200;

	@BeforeEach
	void start() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", exchange -> {
			seenPath.set(exchange.getRequestURI().toString());
			seenAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
			seenApiKey.set(exchange.getRequestHeaders().getFirst("x-api-key"));
			exchange.sendResponseHeaders(answer, -1);
			exchange.close();
		});
		server.start();
		base = "http://127.0.0.1:" + server.getAddress().getPort();
	}

	@AfterEach
	void stop() {
		server.stop(0);
	}

	private final ConnectionTester tester = new ConnectionTester();

	@Test
	void geminiNeedsAKeyAndIsAskedForItsModelListWithIt() {
		assertFalse(tester.test("gemini", base, null));
		assertFalse(tester.test("gemini", base, ""));
		assertTrue(tester.test("gemini", base + "/", "abc 123"));
		assertEquals("/v1beta/models?key=abc+123", seenPath.get());
	}

	@Test
	void anthropicSendsItsKeyAndVersionInHeaders() {
		assertFalse(tester.test("anthropic", base, null));
		assertTrue(tester.test("anthropic", base, "sk-test"));
		assertEquals("/v1/models", seenPath.get());
		assertEquals("sk-test", seenApiKey.get());
	}

	@Test
	void yahooNeedsNoKey() {
		assertTrue(tester.test("yahoo", base, null));
		assertEquals("/v8/finance/chart/AAPL?range=1d&interval=1d", seenPath.get());
	}

	@Test
	void aKnownProviderMustAnswerSuccessAndAServerErrorOrRefusalIsAFailure() {
		answer = 401;
		assertFalse(tester.test("gemini", base, "wrong-key"));
		answer = 500;
		assertFalse(tester.test("yahoo", base, null));
	}

	@Test
	void anyOtherProviderOnlyHasToAnswerAndGetsItsKeyAsABearerToken() {
		answer = 404;
		assertTrue(tester.test("my-bank", base, "tok"), "a 404 still shows the service is there");
		assertEquals("Bearer tok", seenAuth.get());
		assertTrue(tester.test("angel-one", base, null));
		answer = 503;
		assertFalse(tester.test("my-bank", base, null));
	}

	@Test
	void everyTestConnectionIsCountedOnceAsACallToThatProvider() {
		com.wealth.bss.nexus.service.IfApiUsageService usage = org.mockito.Mockito.mock(com.wealth.bss.nexus.service.IfApiUsageService.class);
		ConnectionTester counted = new ConnectionTester(usage);

		answer = 200;
		assertTrue(counted.test("my-bank", base, null));
		org.mockito.Mockito.verify(usage).recordExternal(org.mockito.ArgumentMatchers.eq("my-bank"), org.mockito.ArgumentMatchers.eq("test"),
				org.mockito.ArgumentMatchers.eq(true), org.mockito.ArgumentMatchers.anyLong());

		answer = 500;
		assertFalse(counted.test("my-bank", base, null));
		org.mockito.Mockito.verify(usage).recordExternal(org.mockito.ArgumentMatchers.eq("my-bank"), org.mockito.ArgumentMatchers.eq("test"),
				org.mockito.ArgumentMatchers.eq(false), org.mockito.ArgumentMatchers.anyLong());
	}

	@Test
	void anAddressNobodyAnswersIsAFailureNotAnError() {
		assertFalse(tester.test("my-bank", "http://127.0.0.1:9", null));
		assertFalse(tester.test("my-bank", "http://no-such-host.invalid", null));
	}
}
