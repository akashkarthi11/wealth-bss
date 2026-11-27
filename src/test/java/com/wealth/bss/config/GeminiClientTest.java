package com.wealth.bss.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

// The Gemini call is checked against a local server that answers like Gemini; nothing here reaches the real one.
class GeminiClientTest {

	private HttpServer server;
	private String base;
	private final AtomicReference<String> path = new AtomicReference<>();
	private final AtomicReference<String> key = new AtomicReference<>();
	private final AtomicReference<String> body = new AtomicReference<>();
	private volatile int code = 200;
	private volatile String answer = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"{\\\"rows\\\":[]}\"}]}}]}";

	@BeforeEach
	void start() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", exchange -> {
			path.set(exchange.getRequestURI().toString());
			key.set(exchange.getRequestHeaders().getFirst("x-goog-api-key"));
			body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			reply(exchange, code, answer);
		});
		server.start();
		base = "http://127.0.0.1:" + server.getAddress().getPort();
	}

	@AfterEach
	void stop() {
		server.stop(0);
	}

	@Test
	void theKeyGoesInAHeaderNotTheAddressAndTheAnswerTextIsReturned() {
		String text = new GeminiClient().generateJson(base + "/", "secret-key", "some-model", "the prompt");

		assertEquals("{\"rows\":[]}", text);
		assertEquals("/v1beta/models/some-model:generateContent", path.get());
		assertEquals("secret-key", key.get());
		assertFalse(path.get().contains("secret-key"));
		assertTrue(body.get().contains("the prompt") && body.get().contains("application/json"));
	}

	@Test
	void aRefusalOrAnAnswerWithNoTextIsAnIllegalStateWithoutTheBodyOrTheKey() {
		code = 429;
		answer = "{\"error\":{\"message\":\"quota secret-key\"}}";
		IllegalStateException refused = assertThrows(IllegalStateException.class, () -> new GeminiClient().generateJson(base, "secret-key", "m", "p"));
		assertFalse(refused.getMessage().contains("secret-key") || refused.getMessage().contains("quota"));
		code = 200;
		answer = "{\"candidates\":[]}";
		assertThrows(IllegalStateException.class, () -> new GeminiClient().generateJson(base, "k", "m", "p"));
	}

	private static void reply(HttpExchange exchange, int status, String text) throws IOException {
		byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().add("Content-Type", "application/json");
		exchange.sendResponseHeaders(status, bytes.length);
		exchange.getResponseBody().write(bytes);
		exchange.close();
	}
}
