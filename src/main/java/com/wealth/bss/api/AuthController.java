package com.wealth.bss.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wealth.bss.security.WealthAuthRateLimiter;
import com.wealth.bss.security.WealthPasswordService;
import com.wealth.bss.security.WealthSessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@ConditionalOnProperty(name = "WEALTH_JDBC_URL")
@RequestMapping("/api/auth")
public class AuthController {
    private static final Duration WINDOW = Duration.ofMinutes(15);
    private static final long EMAIL_LIMIT = 5;
    private static final long IP_LIMIT = 20;

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final WealthSessionService sessions;
    private final WealthPasswordService passwords;
    private final WealthAuthRateLimiter limiter;
    private final String accessKey;

    public AuthController(JdbcTemplate jdbc, ObjectMapper mapper, WealthSessionService sessions,
            WealthPasswordService passwords, WealthAuthRateLimiter limiter,
            @Value("${WEALTH_ACCESS_KEY:}") String accessKey) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.sessions = sessions;
        this.passwords = passwords;
        this.limiter = limiter;
        this.accessKey = accessKey;
    }

    @GetMapping
    public ResponseEntity<?> current(HttpServletRequest request) {
        var userId = sessions.authenticatedUser(request);
        if (userId.isEmpty()) return ResponseEntity.ok(Map.of("user", (Object) nullSafeNull()));
        return ResponseEntity.ok(Map.of("user", userPayload(userId.get())));
    }

    @PostMapping
    public ResponseEntity<?> action(@RequestParam(defaultValue = "") String action,
            @RequestBody(required = false) JsonNode body,
            HttpServletRequest request, HttpServletResponse response) {
        JsonNode data = body == null ? mapper.createObjectNode() : body;
        if ("logout".equals(action)) {
            clearCookie(response);
            return ResponseEntity.ok(Map.of("ok", true));
        }
        if (!List.of("login", "register", "reset", "forgot").contains(action))
            return ResponseEntity.badRequest().body(Map.of("error", "Unknown action"));

        String email = data.path("email").asText("").trim().toLowerCase();
        String password = data.path("password").asText("");
        String ip = clientIp(request);
        if (!validEmail(email)) return ResponseEntity.badRequest().body(Map.of("error", "Enter a valid email address."));
        if (password.length() > 200) return ResponseEntity.badRequest().body(Map.of("error", "Password is too long."));

        if ("login".equals(action)) return login(email, password, ip, response);
        if ("register".equals(action)) return register(email, password, data.path("secretKey").asText(""), ip, response);
        return reset(email, password, request, ip, response);
    }

    private ResponseEntity<?> login(String email, String password, String ip, HttpServletResponse response) {
        if (limiter.count("login:ip:" + ip) >= IP_LIMIT || limiter.count("login:email:" + email) >= EMAIL_LIMIT)
            return tooMany(WINDOW.toSeconds());
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT id, email, password_hash, role, session_version FROM wealth_users WHERE email = ?", email);
        if (rows.isEmpty() || !passwords.matches(password, String.valueOf(rows.get(0).get("password_hash")))) {
            var ipDecision = limiter.failedAttempt("login:ip:" + ip, IP_LIMIT, WINDOW);
            var emailDecision = limiter.failedAttempt("login:email:" + email, EMAIL_LIMIT, WINDOW);
            if (!ipDecision.allowed()) return tooMany(ipDecision.retryAfterSeconds());
            if (!emailDecision.allowed()) return tooMany(emailDecision.retryAfterSeconds());
            return ResponseEntity.status(401).body(Map.of("error", "Email or password is incorrect."));
        }
        Map<String, Object> user = rows.get(0);
        limiter.reset("login:email:" + email);
        UUID id = (UUID) user.get("id");
        long version = ((Number) user.get("session_version")).longValue();
        try { setCookie(response, sessions.issueToken(id, version)); }
        catch (Exception ex) { return ResponseEntity.internalServerError().body(Map.of("error", "Unable to create a session.")); }
        return ResponseEntity.ok(Map.of("user", userPayload(id)));
    }

    private ResponseEntity<?> register(String email, String password, String suppliedKey, String ip, HttpServletResponse response) {
        var ipDecision = limiter.failedAttempt("auth:ip:" + ip, IP_LIMIT, WINDOW);
        if (!ipDecision.allowed()) return tooMany(ipDecision.retryAfterSeconds());
        if (!constantTimeEquals(accessKey, suppliedKey))
            return ResponseEntity.status(403).body(Map.of("error", "Secret key is incorrect."));
        String problem = passwords.passwordProblem(password);
        if (problem != null) return ResponseEntity.badRequest().body(Map.of("error", problem));
        List<Map<String, Object>> rows = jdbc.queryForList("""
            INSERT INTO wealth_users(email, password_hash) VALUES (?, ?)
            ON CONFLICT(email) DO NOTHING
            RETURNING id, email, role, session_version
            """, email, passwords.hash(password));
        if (rows.isEmpty()) return ResponseEntity.status(409).body(Map.of("error", "An account with this email already exists."));
        Map<String, Object> user = rows.get(0);
        try { setCookie(response, sessions.issueToken((UUID) user.get("id"), ((Number) user.get("session_version")).longValue())); }
        catch (Exception ex) { return ResponseEntity.internalServerError().body(Map.of("error", "Unable to create a session.")); }
        return ResponseEntity.status(201).body(Map.of("user", userPayload((UUID) user.get("id"))));
    }

    private ResponseEntity<?> reset(String email, String password, HttpServletRequest request,
            String ip, HttpServletResponse response) {
        var ipDecision = limiter.failedAttempt("auth:ip:" + ip, IP_LIMIT, WINDOW);
        if (!ipDecision.allowed()) return tooMany(ipDecision.retryAfterSeconds());
        var callerId = sessions.authenticatedUser(request);
        if (callerId.isEmpty()) return ResponseEntity.status(403).body(Map.of("error",
            "Password reset is done by the account owner while signed in. Contact the owner to reset your password."));
        List<Map<String, Object>> caller = jdbc.queryForList("SELECT id, role FROM wealth_users WHERE id = ?", callerId.get());
        if (caller.isEmpty() || !"admin".equals(caller.get(0).get("role")))
            return ResponseEntity.status(403).body(Map.of("error",
                "Password reset is done by the account owner while signed in. Contact the owner to reset your password."));
        String problem = passwords.passwordProblem(password);
        if (problem != null) return ResponseEntity.badRequest().body(Map.of("error", problem));
        List<Map<String, Object>> updated = jdbc.queryForList("""
            UPDATE wealth_users SET password_hash = ?, session_version = session_version + 1
             WHERE email = ? RETURNING id, session_version
            """, passwords.hash(password), email);
        if (updated.isEmpty()) return ResponseEntity.status(404).body(Map.of("error", "No account was found for that email."));
        UUID updatedId = (UUID) updated.get(0).get("id");
        if (updatedId.equals(callerId.get())) {
            try { setCookie(response, sessions.issueToken(updatedId, ((Number) updated.get(0).get("session_version")).longValue())); }
            catch (Exception ex) { return ResponseEntity.internalServerError().body(Map.of("error", "Password reset; please sign in again.")); }
        }
        return ResponseEntity.ok(Map.of("ok", true, "message", "Password reset. Existing sessions for that account were signed out."));
    }

    private Map<String, Object> userPayload(UUID id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT id, email, role, created_at FROM wealth_users WHERE id = ?", id);
        if (rows.isEmpty()) return Map.of();
        Map<String, Object> user = new LinkedHashMap<>(rows.get(0));
        if ("admin".equals(user.get("role"))) user.put("menus", null);
        else {
            try {
                List<Map<String, Object>> menus = jdbc.queryForList("SELECT menus FROM wealth_role_menus WHERE role = 'user'");
                user.put("menus", menus.isEmpty() ? null : menus.get(0).get("menus"));
            } catch (Exception ignored) { user.put("menus", null); }
        }
        return user;
    }

    private ResponseEntity<?> tooMany(long seconds) {
        return ResponseEntity.status(429).header("Retry-After", Long.toString(Math.max(1, seconds)))
            .body(Map.of("error", "Too many attempts. Please wait and try again."));
    }

    private static boolean validEmail(String email) {
        return email.length() <= 254 && email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) return forwarded.split(",")[0].trim();
        return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
    }

    private static boolean constantTimeEquals(String a, String b) {
        byte[] x = String.valueOf(a).getBytes(StandardCharsets.UTF_8);
        byte[] y = String.valueOf(b).getBytes(StandardCharsets.UTF_8);
        return x.length > 0 && MessageDigest.isEqual(x, y);
    }

    private static void setCookie(HttpServletResponse response, String token) {
        response.addHeader("Set-Cookie", "wealth_session=" + token + "; Path=/; HttpOnly; Secure; SameSite=Lax; Max-Age=604800");
    }

    private static void clearCookie(HttpServletResponse response) {
        response.addHeader("Set-Cookie", "wealth_session=; Path=/; HttpOnly; Secure; SameSite=Lax; Max-Age=0");
    }

    private static Object nullSafeNull() { return null; }
}
