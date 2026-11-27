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

// The Google calls are checked against a local server that answers like Google; nothing here reaches the real one.
class GmailClientTest {

	private HttpServer server;
	private String base;
	private GmailClient client;
	private final AtomicReference<String> tokenBody = new AtomicReference<>();
	private final AtomicReference<String> revokeBody = new AtomicReference<>();
	private final AtomicReference<String> profileAuth = new AtomicReference<>();
	private volatile int tokenCode = 200;
	private volatile String tokenAnswer = "{\"access_token\":\"acc\",\"refresh_token\":\"ref\"}";

	@BeforeEach
	void start() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/token", exchange -> {
			tokenBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			reply(exchange, tokenCode, tokenAnswer);
		});
		server.createContext("/revoke", exchange -> {
			revokeBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			reply(exchange, 200, "{}");
		});
		server.createContext("/gmail/v1/users/me/profile", exchange -> {
			profileAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
			reply(exchange, 200, "{\"emailAddress\":\"karthi@gmail.com\"}");
		});
		server.start();
		base = "http://127.0.0.1:" + server.getAddress().getPort();
		client = new GmailClient(null, base + "/auth", base + "/token", base + "/revoke");
	}

	@AfterEach
	void stop() {
		server.stop(0);
	}

	private static void reply(HttpExchange exchange, int code, String body) throws IOException {
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().add("Content-Type", "application/json");
		exchange.sendResponseHeaders(code, bytes.length);
		exchange.getResponseBody().write(bytes);
		exchange.close();
	}

	@Test
	void theConsentAddressAsksForReadOnlyOfflineAccessAndEncodesEveryValue() {
		String url = client.consentUrl("id 1", "https://app.example.com/cb", "a+b/c=");
		assertTrue(url.startsWith(base + "/auth?client_id=id+1&redirect_uri=https%3A%2F%2Fapp.example.com%2Fcb"));
		assertTrue(url.contains("scope=https%3A%2F%2Fwww.googleapis.com%2Fauth%2Fgmail.readonly"));
		assertTrue(url.contains("response_type=code&"));
		assertTrue(url.contains("access_type=offline"));
		assertTrue(url.contains("prompt=consent"));
		assertTrue(url.endsWith("state=a%2Bb%2Fc%3D"));
		assertFalse(url.contains("gmail.modify") || url.contains("mail.google.com"), "no write scope");
	}

	@Test
	void theCodeIsSwappedForTokensAndAMissingRefreshTokenIsRefused() {
		GmailClient.Tokens tokens = client.exchange("cid", "csecret", "https://app.example.com/cb", "the-code");
		assertEquals("ref", tokens.refreshToken());
		assertEquals("acc", tokens.accessToken());
		assertTrue(tokenBody.get().contains("grant_type=authorization_code"));
		assertTrue(tokenBody.get().contains("code=the-code"));

		tokenAnswer = "{\"access_token\":\"acc\"}";
		assertThrows(IllegalStateException.class, () -> client.exchange("cid", "csecret", "https://app.example.com/cb", "the-code"));
	}

	@Test
	void aRefusalGivesGooglesErrorNameOnlyNeverTheBody() {
		tokenCode = 400;
		tokenAnswer = "{\"error\":\"invalid_grant\",\"error_description\":\"Bad secret-value\"}";
		IllegalStateException e = assertThrows(IllegalStateException.class, () -> client.accessToken("cid", "csecret", "old-refresh"));
		assertTrue(e.getMessage().contains("invalid_grant"));
		assertFalse(e.getMessage().contains("secret-value"));
		assertFalse(e.getMessage().contains("old-refresh"));
	}

	@Test
	void aFreshAccessTokenAndTheMailboxAddress() {
		assertEquals("acc", client.accessToken("cid", "csecret", "ref"));
		assertTrue(tokenBody.get().contains("grant_type=refresh_token"));
		assertEquals("karthi@gmail.com", client.mailboxAddress(base, "acc"));
		assertEquals("Bearer acc", profileAuth.get());
	}

	@Test
	void theMailListGivesOnlyWellFormedIdsAndAMailIsReadFromItsPlainTextOrItsHtml() throws IOException {
		java.util.concurrent.atomic.AtomicReference<String> listQuery = new java.util.concurrent.atomic.AtomicReference<>();
		server.createContext("/gmail/v1/users/me/messages", exchange -> {
			String path = exchange.getRequestURI().getPath();
			if (path.endsWith("/messages")) {
				listQuery.set(exchange.getRequestURI().getRawQuery());
				reply(exchange, 200, "{\"messages\":[{\"id\":\"abc123def456\"},{\"id\":\"../bad\"},{\"id\":\"plain00001\"}]}");
			} else if (path.endsWith("/abc123def456")) {
				String html = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString("<html><style>p{}</style><body><p>Rs.5.00&nbsp;debited</p></body></html>".getBytes(StandardCharsets.UTF_8));
				reply(exchange, 200, "{\"payload\":{\"mimeType\":\"multipart/alternative\",\"headers\":[{\"name\":\"From\",\"value\":\"Bank <alerts@bank.example>\"}],\"parts\":[{\"mimeType\":\"text/html\",\"body\":{\"data\":\"" + html + "\"}}]}}");
			} else {
				String text = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString("Hello   plain\nworld".getBytes(StandardCharsets.UTF_8));
				reply(exchange, 200, "{\"payload\":{\"mimeType\":\"text/plain\",\"headers\":[{\"name\":\"from\",\"value\":\"a@b.example\"}],\"body\":{\"data\":\"" + text + "\"}}}");
			}
		});

		assertEquals(java.util.List.of("abc123def456", "plain00001"), client.messageIds(base, "acc", "from:a newer_than:35d", 50));
		assertTrue(listQuery.get().contains("maxResults=50") && listQuery.get().contains("q=from%3Aa+newer_than%3A35d"));

		GmailClient.Message html = client.message(base, "acc", "abc123def456");
		assertEquals("Bank <alerts@bank.example>", html.from());
		assertEquals("Rs.5.00 debited", html.text());
		assertEquals("Hello plain world", client.message(base, "acc", "plain00001").text());
		assertThrows(IllegalStateException.class, () -> client.message(base, "acc", "../bad"));
	}

	@Test
	void pdfAttachmentsAreFoundByFileNameAndDownloadedInMemory() throws IOException {
		byte[] bytes = "%PDF-fake-bytes".getBytes(StandardCharsets.UTF_8);
		server.createContext("/gmail/v1/users/me/messages/abc123def456", exchange -> {
			String path = exchange.getRequestURI().getPath();
			if (path.endsWith("/attachments/att_one-0000001")) {
				reply(exchange, 200, "{\"data\":\"" + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes) + "\"}");
			} else {
				reply(exchange, 200, "{\"payload\":{\"mimeType\":\"multipart/mixed\",\"parts\":[{\"mimeType\":\"text/plain\",\"filename\":\"\",\"body\":{\"data\":\"aGk\"}},"
						+ "{\"mimeType\":\"application/pdf\",\"filename\":\"Statement_Oct.PDF\",\"body\":{\"attachmentId\":\"att_one-0000001\"}},"
						+ "{\"mimeType\":\"image/png\",\"filename\":\"logo.png\",\"body\":{\"attachmentId\":\"att_img-0000002\"}}]}}");
			}
		});

		java.util.List<GmailClient.Attachment> found = client.pdfAttachments(base, "acc", "abc123def456");
		assertEquals(java.util.List.of(new GmailClient.Attachment("Statement_Oct.PDF", "att_one-0000001")), found);
		assertEquals(new String(bytes, StandardCharsets.UTF_8), new String(client.attachment(base, "acc", "abc123def456", "att_one-0000001"), StandardCharsets.UTF_8));
		assertThrows(IllegalStateException.class, () -> client.attachment(base, "acc", "abc123def456", "../x"));
		assertThrows(IllegalStateException.class, () -> client.pdfAttachments(base, "acc", "../bad"));
	}

	@Test
	void revokeSendsTheTokenAndNeverFailsTheCaller() {
		client.revoke("ref");
		assertTrue(revokeBody.get().contains("token=ref"));
		server.stop(0);
		client.revoke("ref");
	}
}
