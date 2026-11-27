package com.wealth.bss.config;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.wealth.bss.nexus.service.IfApiUsageService;
import com.wealth.common.logger.KarthiLogger;

// Test connection for a third-party service: one small request to its address, with the key the way that service wants it. The answer is only
// "worked or not": the response body is never read into the app, and the key is never logged. A redirect is not followed.
@Component
public class ConnectionTester {

	private static final KarthiLogger LOGGER = new KarthiLogger(ConnectionTester.class);
	private static final int SECONDS = 8;
	private static final int SERVER_ERROR = 500;

	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(SECONDS)).followRedirects(HttpClient.Redirect.NEVER).build();
	// Counts every Test connection as one outgoing call to that provider (API usage). Null in a test that does not need counting.
	private final IfApiUsageService usage;

	public ConnectionTester() {
		this(null);
	}

	@Autowired
	public ConnectionTester(IfApiUsageService usage) {
		this.usage = usage;
	}

	// One Test connection, counted once with its result and time.
	public boolean test(String provider, String baseUrl, String apiKey) {
		long started = System.nanoTime();
		boolean worked = probe(provider, baseUrl, apiKey);
		if (usage != null) {
			usage.recordExternal(provider, "test", worked, (System.nanoTime() - started) / 1_000_000L);
		}
		return worked;
	}

	// apiKey may be null. Gemini, Anthropic and Yahoo are called on a cheap authenticated (or public) path and must answer 2xx. Any other provider,
	// and Angel One (its sign-in needs more than a key), only has to answer, with its key as a bearer token when one is set.
	private boolean probe(String provider, String baseUrl, String apiKey) {
		String base = baseUrl.replaceAll("/+$", "");
		boolean hasKey = apiKey != null && !apiKey.isEmpty();
		try {
			HttpRequest.Builder request;
			boolean mustBeOk = true;
			switch (provider) {
			case "gemini":
				if (!hasKey) {
					return false;
				}
				request = HttpRequest.newBuilder(URI.create(base + "/v1beta/models?key=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8)));
				break;
			case "anthropic":
				if (!hasKey) {
					return false;
				}
				request = HttpRequest.newBuilder(URI.create(base + "/v1/models")).header("x-api-key", apiKey).header("anthropic-version", "2023-06-01");
				break;
			case "yahoo":
				request = HttpRequest.newBuilder(URI.create(base + "/v8/finance/chart/AAPL?range=1d&interval=1d")).header("User-Agent", "Mozilla/5.0");
				break;
			default:
				request = HttpRequest.newBuilder(URI.create(base));
				if (hasKey) {
					request.header("Authorization", "Bearer " + apiKey);
				}
				mustBeOk = false;
				break;
			}
			HttpResponse<Void> response = http.send(request.timeout(Duration.ofSeconds(SECONDS)).GET().build(), HttpResponse.BodyHandlers.discarding());
			int status = response.statusCode();
			return mustBeOk ? status >= 200 && status < 300 : status < SERVER_ERROR;
		} catch (Exception e) {
			LOGGER.warn("Connection test failed for " + provider + ": " + e.getClass().getSimpleName());
			return false;
		}
	}
}
