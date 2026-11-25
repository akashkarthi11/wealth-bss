package com.wealth.bss.api;

import com.wealth.bss.security.WealthSessionService;
import org.springframework.beans.factory.ObjectProvider;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/wealth-service/api/v1/clear-session")
public class ClearSessionController {
    private final ObjectProvider<JdbcTemplate> jdbcProvider;
    private final ObjectProvider<WealthSessionService> sessionsProvider;

    public ClearSessionController(ObjectProvider<JdbcTemplate> jdbcProvider,
            ObjectProvider<WealthSessionService> sessionsProvider) {
        this.jdbcProvider = jdbcProvider;
        this.sessionsProvider = sessionsProvider;
    }

    @GetMapping
    public ResponseEntity<Void> clearThisBrowser(HttpServletResponse response) {
        clearCookie(response);
        return ResponseEntity.status(303)
            .header(HttpHeaders.LOCATION, "/")
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .build();
    }

    @PostMapping
    public ResponseEntity<?> clearEverywhere(HttpServletRequest request, HttpServletResponse response) {
        boolean everywhere = false;
        JdbcTemplate jdbc = jdbcProvider.getIfAvailable();
        WealthSessionService sessions = sessionsProvider.getIfAvailable();
        var claims = sessions == null ? java.util.Optional.<WealthSessionService.SessionClaims>empty() : sessions.sessionClaims(request);
        if (jdbc != null && claims.isPresent()) {
            try {
                Integer changed = jdbc.queryForObject("""
                    WITH actor AS (SELECT set_config('wealth.actor', 'owner', true))
                    UPDATE wealth_users
                       SET session_version = COALESCE(session_version, 1) + 1
                      FROM actor
                     WHERE id = ? AND COALESCE(session_version, 1) = ?
                    RETURNING 1
                    """, Integer.class, claims.get().userId(), claims.get().sessionVersion());
                everywhere = changed != null;
            } catch (Exception ex) {
                // Always clear this browser even if the database cannot revoke other devices.
                System.err.println("[wealth-clear-session] " + ex.getMessage());
            }
        }
        clearCookie(response);
        return ResponseEntity.ok()
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .body(Map.of("ok", true, "everywhere", everywhere));
    }

    private static void clearCookie(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE,
            "wealth_session=; Path=/; HttpOnly; Secure; SameSite=Lax; Max-Age=0");
    }
}
