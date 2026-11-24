package com.wealth.bss.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "WEALTH_JDBC_URL")
public class WealthSessionService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final String secret;

    public WealthSessionService(JdbcTemplate jdbc, ObjectMapper mapper,
            @Value("${WEALTH_SESSION_SECRET:}") String secret) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.secret = secret;
    }

    public Optional<UUID> authenticatedUser(HttpServletRequest request) {
        if (secret == null || secret.trim().length() < 32) return Optional.empty();
        try {
            String cookieHeader = request.getHeader("Cookie");
            if (cookieHeader == null) return Optional.empty();
            String token = null;
            for (String part : cookieHeader.split(";")) {
                String item = part.trim();
                if (item.startsWith("wealth_session=")) {
                    token = item.substring("wealth_session=".length());
                    break;
                }
            }
            if (token == null) return Optional.empty();
            String[] pieces = token.split("\\.", -1);
            if (pieces.length != 2) return Optional.empty();
            byte[] expected = hmac(pieces[0]);
            byte[] supplied = Base64.getUrlDecoder().decode(pieces[1]);
            if (!MessageDigest.isEqual(expected, supplied)) return Optional.empty();

            byte[] payload = Base64.getUrlDecoder().decode(pieces[0]);
            JsonNode claims = mapper.readTree(payload);
            UUID userId = UUID.fromString(claims.path("uid").asText());
            long expiresAt = claims.path("exp").asLong(0);
            long sessionVersion = claims.path("sv").asLong(1);
            if (expiresAt <= System.currentTimeMillis()) return Optional.empty();

            Integer currentVersion = jdbc.queryForObject(
                "SELECT session_version FROM wealth_users WHERE id = ?",
                Integer.class, userId);
            if (currentVersion == null || currentVersion.longValue() != sessionVersion) {
                return Optional.empty();
            }
            return Optional.of(userId);
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private byte[] hmac(String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
    }
}
