package com.wealth.bss.config;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriUtils;

@Component
public class FinvuFiuClient {
	private final RestClient client;
	private final String baseUrl;
	private final String token;

	public FinvuFiuClient(RestClient.Builder builder,
			@Value("${wealth.finvu.fiu-base-url:https://finsense.finvu.in/ConnectHub/FIU/API/V1}") String baseUrl,
			@Value("${wealth.finvu.fiu-token:}") String token) {
		this.baseUrl = baseUrl.replaceAll("/+$", "");
		this.token = token == null ? "" : token.trim();
		this.client = builder.build();
	}

	public Map<String, Object> configuration() {
		return Map.of("provider", "finvu", "mode", "FIU_SANDBOX", "configured", !token.isBlank(),
				"baseUrl", baseUrl, "status", token.isBlank() ? "NOT_CONFIGURED" : "READY");
	}

	public Map<String, Object> requestConsent(String custId, String templateName, String description) {
		requireConfigured();
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("custId", custId);
		body.put("templateName", templateName);
		body.put("consentDescription", description);
		return post("SubmitConsentRequest", body);
	}

	public Map<String, Object> consentStatus(String custId, String consentHandleId) {
		requireConfigured();
		return get("ConsentStatus/" + enc(consentHandleId) + "/" + enc(custId));
	}

	public Map<String, Object> requestFinancialInformation(String custId, String consentHandleId, String consentId,
			String from, String to) {
		requireConfigured();
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("custId", custId);
		body.put("consentId", consentId);
		body.put("consentHandleId", consentHandleId);
		body.put("dateTimeRangeFrom", from);
		body.put("dateTimeRangeTo", to);
		return post("FIRequest", body);
	}

	public Map<String, Object> financialInformationStatus(String custId, String consentHandleId, String consentId, String sessionId) {
		requireConfigured();
		return get("FIStatus/" + enc(consentId) + "/" + enc(sessionId) + "/" + enc(consentHandleId) + "/" + enc(custId));
	}

	public Map<String, Object> fetchFinancialInformation(String custId, String consentId, String sessionId) {
		requireConfigured();
		return get("FIFetch/" + enc(custId) + "/" + enc(consentId) + "/" + enc(sessionId));
	}

	private Map<String, Object> post(String path, Map<String, Object> body) {
		return client.post().uri(baseUrl + "/" + path).headers(this::headers).contentType(MediaType.APPLICATION_JSON)
				.body(envelope(body)).retrieve().body(Map.class);
	}

	private Map<String, Object> get(String path) {
		return client.get().uri(baseUrl + "/" + path).headers(this::headers).accept(MediaType.APPLICATION_JSON)
				.retrieve().body(Map.class);
	}

	private Map<String, Object> envelope(Map<String, Object> body) {
		Map<String, Object> header = new LinkedHashMap<>();
		header.put("rid", UUID.randomUUID().toString());
		header.put("ts", OffsetDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ")));
		header.put("channelId", "wealth");
		return Map.of("header", header, "body", body);
	}

	private void headers(HttpHeaders headers) {
		headers.setBearerAuth(token);
		headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
	}

	private void requireConfigured() {
		if (token.isBlank()) throw new IllegalStateException("Finvu FIU sandbox token is not configured. Set WEALTH_FINVU_FIU_TOKEN on the BSS server.");
	}

	private String enc(String value) {
		return UriUtils.encodePathSegment(value, java.nio.charset.StandardCharsets.UTF_8);
	}
}
