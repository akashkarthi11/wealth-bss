package com.wealth.bss.api;

import com.wealth.bss.security.WealthSessionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/accounts")
public class AccountsController {
    private final JdbcTemplate jdbc;
    private final WealthSessionService sessions;

    public AccountsController(JdbcTemplate jdbc, WealthSessionService sessions) {
        this.jdbc = jdbc;
        this.sessions = sessions;
    }

    @GetMapping
    public ResponseEntity<?> list(HttpServletRequest request) {
        var user = sessions.authenticatedUser(request);
        if (user.isEmpty()) return ResponseEntity.status(401).body(Map.of("error", "Sign in required"));
        List<Map<String, Object>> accounts = jdbc.queryForList("""
            SELECT id, account_key, bank_name, account_name, account_type,
                   masked_identifier, currency, current_balance, balance_as_of, balance_status
              FROM wealth_accounts
             WHERE user_id = ?
             ORDER BY bank_name, account_name
            """, user.get());
        return ResponseEntity.ok(Map.of("accounts", accounts));
    }
}
