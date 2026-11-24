package com.wealth.bss.api;

import com.wealth.bss.security.WealthSessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/ledger")
public class LedgerController {
    private final JdbcTemplate jdbc;
    private final WealthSessionService sessions;

    public LedgerController(JdbcTemplate jdbc, WealthSessionService sessions) {
        this.jdbc = jdbc;
        this.sessions = sessions;
    }

    public record CreateTransaction(
        @NotBlank @Pattern(regexp = "income|expense|transfer|investment|liability") String kind,
        @NotBlank @Size(max = 60) String category,
        @Size(max = 180) String description,
        @DecimalMin(value = "0.00000001") @Digits(integer = 12, fraction = 8) BigDecimal amount,
        String accountId,
        @NotBlank @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}") String date,
        @Size(max = 120) String externalRef
    ) {}

    @GetMapping
    public ResponseEntity<?> list(HttpServletRequest request,
            @RequestParam(defaultValue = "500") int limit) {
        var user = sessions.authenticatedUser(request);
        if (user.isEmpty()) return ResponseEntity.status(401).body(Map.of("error", "Sign in required"));
        int safeLimit = Math.max(1, Math.min(limit, 500));
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT id, kind, category, description, amount, account_id, occurred_on,
                   created_at, status, external_ref
              FROM wealth_transactions
             WHERE user_id = ?
             ORDER BY occurred_on DESC, created_at DESC
             LIMIT ?
            """, user.get(), safeLimit);
        return ResponseEntity.ok(Map.of("transactions", rows));
    }

    @PostMapping
    public ResponseEntity<?> create(HttpServletRequest request, @Valid @RequestBody CreateTransaction input) {
        var user = sessions.authenticatedUser(request);
        if (user.isEmpty()) return ResponseEntity.status(401).body(Map.of("error", "Sign in required"));
        UUID accountId = null;
        try {
            if (input.accountId() != null && !input.accountId().isBlank()) {
                accountId = UUID.fromString(input.accountId());
                Integer owned = jdbc.queryForObject(
                    "SELECT count(*) FROM wealth_accounts WHERE user_id = ? AND id = ?",
                    Integer.class, user.get(), accountId);
                if (owned == null || owned == 0)
                    return ResponseEntity.badRequest().body(Map.of("error", "Invalid account"));
            }
            LocalDate date = LocalDate.parse(input.date());
            if (input.amount() == null || input.amount().signum() <= 0 ||
                    input.amount().compareTo(new BigDecimal("1000000000000")) > 0)
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid transaction amount"));

            if (input.externalRef() != null && !input.externalRef().isBlank()) {
                Integer duplicate = jdbc.queryForObject("""
                    SELECT count(*) FROM wealth_transactions
                     WHERE user_id = ? AND external_ref = ?
                    """, Integer.class, user.get(), input.externalRef().trim());
                if (duplicate != null && duplicate > 0)
                    return ResponseEntity.status(409).body(Map.of("error", "This transaction reference is already recorded"));
            }

            Map<String, Object> saved = jdbc.queryForMap("""
                INSERT INTO wealth_transactions
                    (user_id, kind, category, description, amount, account, account_id, occurred_on, external_ref)
                VALUES (?, ?, ?, ?, ?, '', ?, ?, ?)
                RETURNING id, kind, category, description, amount, account_id, occurred_on, status, external_ref
                """, user.get(), input.kind(), input.category().trim(),
                input.description() == null ? "" : input.description().trim(),
                input.amount(), accountId, date,
                input.externalRef() == null || input.externalRef().isBlank() ? null : input.externalRef().trim());
            return ResponseEntity.status(201).body(Map.of("transaction", saved));
        } catch (java.time.DateTimeException | IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid transaction details"));
        } catch (org.springframework.dao.DuplicateKeyException ex) {
            return ResponseEntity.status(409).body(Map.of("error", "This transaction reference is already recorded"));
        }
    }
}
