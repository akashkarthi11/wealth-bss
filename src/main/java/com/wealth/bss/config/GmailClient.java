package com.wealth.bss.config;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wealth.bss.nexus.service.IfApiUsageService;
import com.wealth.common.logger.KarthiLogger;

// Google calls for the Gmail connection: the consent address, the code exchange, a fresh access token, the mailbox address and revoking a token.
// Read-only mail access (gmail.readonly). Every call is counted once as outside usage (provider gmail). Secrets and tokens are never logged.
@Component
public class GmailClient {

	private static final KarthiLogger LOGGER = new KarthiLogger(GmailClient.class);
	private static final String PROVIDER = "gmail";
	private static final String AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth";
	private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
	private static final String REVOKE_URL = "https://oauth2.googleapis.com/revoke";
	private static final String PROFILE_PATH = "/gmail/v1/users/me/profile";
	private static final String MESSAGES_PATH = "/gmail/v1/users/me/messages";
	private static final java.util.regex.Pattern MESSAGE_ID = java.util.regex.Pattern.compile("^[A-Za-z0-9]{6,40}$");
	private static final java.util.regex.Pattern ATTACHMENT_ID = java.util.regex.Pattern.compile("^[A-Za-z0-9_-]{10,2000}$");
	private static final String SCOPE = "https://www.googleapis.com/auth/gmail.readonly";
	private static final int SECONDS = 15;

	// What the code exchange returns: the refresh token that is kept, and the access token for the first read.
	public record Tokens(String refreshToken, String accessToken) {
	}

	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(SECONDS)).followRedirects(HttpClient.Redirect.NEVER).build();
	private final ObjectMapper mapper = new ObjectMapper();
	private final IfApiUsageService usage;
	private final String authUrl;
	private final String tokenUrl;
	private final String revokeUrl;

	public GmailClient() {
		this(null);
	}

	@Autowired
	public GmailClient(IfApiUsageService usage) {
		this(usage, AUTH_URL, TOKEN_URL, REVOKE_URL);
	}

	// A test points the three Google addresses at a local server.
	GmailClient(IfApiUsageService usage, String authUrl, String tokenUrl, String revokeUrl) {
		this.usage = usage;
		this.authUrl = authUrl;
		this.tokenUrl = tokenUrl;
		this.revokeUrl = revokeUrl;
	}

	// The Google consent page; offline access and a forced consent screen, so a refresh token is always returned.
	public String consentUrl(String clientId, String redirectUri, String state) {
		return authUrl + "?client_id=" + encode(clientId) + "&redirect_uri=" + encode(redirectUri) + "&response_type=code&scope=" + encode(SCOPE)
				+ "&access_type=offline&prompt=consent&state=" + encode(state);
	}

	// Swaps the one-time code for tokens; IllegalStateException with a short reason when Google refuses or sends no refresh token.
	public Tokens exchange(String clientId, String clientSecret, String redirectUri, String code) {
		Map<String, String> form = new LinkedHashMap<>();
		form.put("client_id", clientId);
		form.put("client_secret", clientSecret);
		form.put("redirect_uri", redirectUri);
		form.put("code", code);
		form.put("grant_type", "authorization_code");
		JsonNode answer = send("token", post(tokenUrl, form));
		String refresh = answer.path("refresh_token").asText("");
		String access = answer.path("access_token").asText("");
		if (refresh.isEmpty() || access.isEmpty()) {
			throw new IllegalStateException("Google did not return a refresh token");
		}
		return new Tokens(refresh, access);
	}

	// A fresh access token from the saved refresh token; IllegalStateException when Google no longer accepts it (revoked or expired).
	public String accessToken(String clientId, String clientSecret, String refreshToken) {
		Map<String, String> form = new LinkedHashMap<>();
		form.put("client_id", clientId);
		form.put("client_secret", clientSecret);
		form.put("refresh_token", refreshToken);
		form.put("grant_type", "refresh_token");
		String access = send("token", post(tokenUrl, form)).path("access_token").asText("");
		if (access.isEmpty()) {
			throw new IllegalStateException("Google did not return an access token");
		}
		return access;
	}

	// The mailbox address the access token belongs to.
	public String mailboxAddress(String baseUrl, String accessToken) {
		HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + PROFILE_PATH)).timeout(Duration.ofSeconds(SECONDS))
				.header("Authorization", "Bearer " + accessToken).header("Accept", "application/json").GET().build();
		String address = send("profile", request).path("emailAddress").asText("");
		if (address.isEmpty()) {
			throw new IllegalStateException("Google did not return the mailbox address");
		}
		return address;
	}

	// One mail as the parser needs it: who sent it and its text (plain text parts, or the HTML parts with the tags removed).
	public record Message(String from, String text) {
	}

	// The ids of the mails that match a Gmail search, newest first, at most max.
	public List<String> messageIds(String baseUrl, String accessToken, String query, int max) {
		HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + MESSAGES_PATH + "?maxResults=" + max + "&q=" + encode(query))).timeout(Duration.ofSeconds(SECONDS))
				.header("Authorization", "Bearer " + accessToken).header("Accept", "application/json").GET().build();
		List<String> ids = new ArrayList<>();
		for (JsonNode item : send("list", request).path("messages")) {
			String id = item.path("id").asText("");
			if (MESSAGE_ID.matcher(id).matches()) {
				ids.add(id);
			}
		}
		return ids;
	}

	// One mail by id; IllegalStateException when Google refuses it.
	public Message message(String baseUrl, String accessToken, String id) {
		if (!MESSAGE_ID.matcher(id).matches()) {
			throw new IllegalStateException("Not a mail id");
		}
		HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + MESSAGES_PATH + "/" + id + "?format=full")).timeout(Duration.ofSeconds(SECONDS))
				.header("Authorization", "Bearer " + accessToken).header("Accept", "application/json").GET().build();
		JsonNode mail = send("message", request);
		String from = "";
		for (JsonNode header : mail.path("payload").path("headers")) {
			if ("From".equalsIgnoreCase(header.path("name").asText())) {
				from = header.path("value").asText("");
			}
		}
		StringBuilder plain = new StringBuilder();
		StringBuilder html = new StringBuilder();
		collect(mail.path("payload"), plain, html);
		String text = plain.length() > 0 ? plain.toString() : html.toString().replaceAll("(?s)<(style|script).*?</\\1>", " ").replaceAll("<[^>]+>", " ");
		return new Message(from, text.replace("&nbsp;", " ").replace("&amp;", "&").replaceAll("\\s+", " ").trim());
	}

	// A PDF attached to a mail: its file name and the id to download it with.
	public record Attachment(String name, String id) {
	}

	// The PDF attachments of one mail (by file name), however deep the parts are nested.
	public List<Attachment> pdfAttachments(String baseUrl, String accessToken, String messageId) {
		if (!MESSAGE_ID.matcher(messageId).matches()) {
			throw new IllegalStateException("Not a mail id");
		}
		HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + MESSAGES_PATH + "/" + messageId + "?format=full")).timeout(Duration.ofSeconds(SECONDS))
				.header("Authorization", "Bearer " + accessToken).header("Accept", "application/json").GET().build();
		List<Attachment> found = new ArrayList<>();
		findPdfs(send("message", request).path("payload"), found);
		return found;
	}

	// The bytes of one attachment, in memory only.
	public byte[] attachment(String baseUrl, String accessToken, String messageId, String attachmentId) {
		if (!MESSAGE_ID.matcher(messageId).matches() || !ATTACHMENT_ID.matcher(attachmentId).matches()) {
			throw new IllegalStateException("Not an attachment id");
		}
		HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + MESSAGES_PATH + "/" + messageId + "/attachments/" + attachmentId)).timeout(Duration.ofSeconds(SECONDS))
				.header("Authorization", "Bearer " + accessToken).header("Accept", "application/json").GET().build();
		String data = send("attachment", request).path("data").asText("");
		if (data.isEmpty()) {
			throw new IllegalStateException("Google sent no attachment data");
		}
		return Base64.getUrlDecoder().decode(data);
	}

	private void findPdfs(JsonNode part, List<Attachment> found) {
		String name = part.path("filename").asText("");
		String id = part.path("body").path("attachmentId").asText("");
		if (name.toLowerCase(java.util.Locale.ROOT).endsWith(".pdf") && !id.isEmpty()) {
			found.add(new Attachment(name, id));
		}
		for (JsonNode child : part.path("parts")) {
			findPdfs(child, found);
		}
	}

	// The text/plain and text/html bodies of a mail, however deep the parts are nested.
	private void collect(JsonNode part, StringBuilder plain, StringBuilder html) {
		String type = part.path("mimeType").asText("");
		String data = part.path("body").path("data").asText("");
		if (!data.isEmpty() && ("text/plain".equals(type) || "text/html".equals(type))) {
			String text = new String(Base64.getUrlDecoder().decode(data), StandardCharsets.UTF_8);
			("text/plain".equals(type) ? plain : html).append(' ').append(text);
		}
		for (JsonNode child : part.path("parts")) {
			collect(child, plain, html);
		}
	}

	// Takes the grant back at Google. Best effort: a failure is logged and the caller still removes its own copy.
	public void revoke(String token) {
		try {
			send("revoke", post(revokeUrl, Map.of("token", token)));
		} catch (IllegalStateException e) {
			LOGGER.warn("Google did not confirm the revoke: " + e.getMessage());
		}
	}

	private HttpRequest post(String url, Map<String, String> form) {
		StringBuilder body = new StringBuilder();
		form.forEach((key, value) -> body.append(body.length() == 0 ? "" : "&").append(encode(key)).append('=').append(encode(value)));
		return HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(SECONDS)).header("Content-Type", "application/x-www-form-urlencoded")
				.header("Accept", "application/json").POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
	}

	// One call to Google, counted once with its result and time, whatever the answer. endpoint is a short name (token, profile), never the address.
	private JsonNode send(String endpoint, HttpRequest request) {
		long started = System.nanoTime();
		boolean worked = false;
		try {
			JsonNode answer = exchange(request);
			worked = true;
			return answer;
		} catch (IOException e) {
			throw new IllegalStateException("Google could not be reached");
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("The Google call was interrupted");
		} finally {
			if (usage != null) {
				usage.recordExternal(PROVIDER, endpoint, worked, (System.nanoTime() - started) / 1_000_000L);
			}
		}
	}

	// The answer as JSON; a refusal is an IllegalStateException with Google's short error name only (never the body, which can carry tokens).
	private JsonNode exchange(HttpRequest request) throws IOException, InterruptedException {
		HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
		JsonNode body;
		try {
			body = mapper.readTree(response.body());
		} catch (IOException e) {
			body = mapper.createObjectNode();
		}
		if (body == null) {
			body = mapper.createObjectNode();
		}
		if (response.statusCode() < 200 || response.statusCode() >= 300) {
			String error = body.path("error").isTextual() ? body.path("error").asText("") : body.path("error").path("status").asText("");
			LOGGER.warn("Google refused a request: HTTP " + response.statusCode());
			throw new IllegalStateException("Google refused the request (" + (error.isEmpty() ? "HTTP " + response.statusCode() : error.replaceAll("[^A-Za-z0-9_ -]", "")) + ")");
		}
		return body;
	}

	private static String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}
}
