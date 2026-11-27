package com.wealth.bss.config;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wealth.bss.nexus.service.IfApiUsageService;
import com.wealth.common.logger.KarthiLogger;

// One call to the Gemini API: a prompt in, the model's JSON answer out. The key goes in a header, never in the address, and is never logged. Every call is counted once as outside usage (provider gemini).
@Component
public class GeminiClient {

	private static final KarthiLogger LOGGER = new KarthiLogger(GeminiClient.class);
	private static final String PROVIDER = "gemini";
	private static final int SECONDS = 90;

	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).followRedirects(HttpClient.Redirect.NEVER).build();
	private final ObjectMapper mapper = new ObjectMapper();
	private final IfApiUsageService usage;

	public GeminiClient() {
		this(null);
	}

	@Autowired
	public GeminiClient(IfApiUsageService usage) {
		this.usage = usage;
	}

	// The text the model answered, asked to be JSON and not creative (temperature 0). IllegalStateException when Gemini cannot be reached, refuses, or answers with no text.
	public String generateJson(String baseUrl, String apiKey, String model, String prompt) {
		ObjectNode body = mapper.createObjectNode();
		body.putArray("contents").addObject().putArray("parts").addObject().put("text", prompt);
		ObjectNode config = body.putObject("generationConfig");
		config.put("responseMimeType", "application/json");
		config.put("temperature", 0);
		HttpRequest request;
		try {
			request = HttpRequest.newBuilder(URI.create(baseUrl.replaceAll("/+$", "") + "/v1beta/models/" + model + ":generateContent")).timeout(Duration.ofSeconds(SECONDS))
					.header("x-goog-api-key", apiKey).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build();
		} catch (IOException | IllegalArgumentException e) {
			throw new IllegalStateException("The Gemini request could not be built");
		}
		long started = System.nanoTime();
		boolean worked = false;
		try {
			HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				LOGGER.warn("Gemini refused a request: HTTP " + response.statusCode());
				throw new IllegalStateException("Gemini refused the request (HTTP " + response.statusCode() + ")");
			}
			JsonNode answer = mapper.readTree(response.body());
			String text = answer == null ? "" : answer.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText("");
			if (text.isBlank()) {
				throw new IllegalStateException("Gemini sent no answer");
			}
			worked = true;
			return text;
		} catch (IOException e) {
			throw new IllegalStateException("Gemini could not be reached");
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("The Gemini call was interrupted");
		} finally {
			if (usage != null) {
				usage.recordExternal(PROVIDER, "generate", worked, (System.nanoTime() - started) / 1_000_000L);
			}
		}
	}
}
