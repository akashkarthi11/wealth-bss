package com.wealth.bss.config;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.wealth.bss.constants.WealthServiceConstants;
import com.wealth.bss.nexus.service.IfApiUsageService;
import com.wealth.common.logger.KarthiLogger;

// INDstocks calls. Read-only: the token goes in the Authorization header as given (no Bearer prefix) and only to an https address (or a local one for tests).
// Every call is counted once as outside usage (provider indstocks). The token and the answer body are never logged.
@Component
public class IndstocksClient {

	private static final KarthiLogger LOGGER = new KarthiLogger(IndstocksClient.class);
	private static final String PROVIDER = "indstocks";
	private static final int SECONDS = 15;

	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(SECONDS)).followRedirects(HttpClient.Redirect.NEVER).build();
	private final IfApiUsageService usage;

	public IndstocksClient() {
		this(null);
	}

	@Autowired
	public IndstocksClient(IfApiUsageService usage) {
		this.usage = usage;
	}

	// One read of the holdings endpoint to prove the token: returns the HTTP status (2xx works, 401 or 403 means INDstocks refused the token).
	// A network failure or a bad address is an IllegalStateException with a short message, never the token.
	public int probe(String baseUrl, String token) {
		String root = baseUrl == null ? "" : baseUrl.trim();
		while (root.endsWith("/")) {
			root = root.substring(0, root.length() - 1);
		}
		if (token == null || token.isBlank() || !(root.startsWith("https://") || root.startsWith("http://127.0.0.1") || root.startsWith("http://localhost"))) {
			throw new IllegalStateException("The INDstocks address or token is not usable");
		}
		HttpRequest request = HttpRequest.newBuilder(URI.create(root + WealthServiceConstants.INDSTOCKS_PROBE_PATH)).timeout(Duration.ofSeconds(SECONDS))
				.header("Authorization", token).header("Accept", "application/json").GET().build();
		long started = System.nanoTime();
		int status = 0;
		try {
			status = http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
			return status;
		} catch (IOException e) {
			throw new IllegalStateException("INDstocks could not be reached");
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("The INDstocks call was interrupted");
		} finally {
			boolean worked = status >= 200 && status < 300;
			if (!worked) {
				LOGGER.warn("INDstocks probe did not succeed: HTTP " + status);
			}
			if (usage != null) {
				usage.recordExternal(PROVIDER, "holdings-check", worked, (System.nanoTime() - started) / 1_000_000L);
			}
		}
	}
}
