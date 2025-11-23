package com.bank.api.service;

import com.bank.api.dto.CommandResponse;
import com.bank.core.state.AccountState;
import com.bank.domain.Account;
import com.bank.domain.AccountStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AdminService {
    private final JdbcTemplate jdbcTemplate;

    public AdminService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public CommandResponse createUser(String nickname) {
        try {
            UUID userId = UUID.randomUUID();
            jdbcTemplate.update("INSERT INTO \"users\" (\"id\", \"nickname\") VALUES (?, ?)", userId, nickname);
            return CommandResponse.ok("User created", userId);
        } catch (Exception e) {
            return CommandResponse.error("Error creating user: " + e.getMessage());
        }
    }

    public CommandResponse createAccount(UUID userId) {
        try {
            Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM \"users\" WHERE \"id\" = ?", Integer.class, userId);
            if (count == null || count == 0) return CommandResponse.error("User not found");

            UUID accountId = UUID.randomUUID();
            BigDecimal initialBalance = BigDecimal.ZERO;

            jdbcTemplate.update("INSERT INTO \"accounts\" (\"id\", \"balance\", \"status\") VALUES (?, ?, 'ACTIVE')",
                    accountId, initialBalance);
            
            jdbcTemplate.update("INSERT INTO \"user_accounts\" (\"user_id\", \"account_id\") VALUES (?, ?)",
                    userId, accountId);

            reloadEngineState();

            return CommandResponse.ok("Account created", accountId);
        } catch (Exception e) {
            return CommandResponse.error("Error creating account: " + e.getMessage());
        }
    }

    public CommandResponse resetSystem() {
        try {
            jdbcTemplate.execute("DELETE FROM \"user_accounts\"");
            jdbcTemplate.execute("DELETE FROM \"transaction_journal\"");
            jdbcTemplate.execute("DELETE FROM \"processed_transactions\"");
            jdbcTemplate.execute("DELETE FROM \"transaction_outbox_dlq\"");
            jdbcTemplate.execute("DELETE FROM \"transaction_outbox\"");
            jdbcTemplate.execute("DELETE FROM \"idempotency_keys\"");
            jdbcTemplate.execute("DELETE FROM \"accounts\"");
            jdbcTemplate.execute("DELETE FROM \"users\"");
            
            reloadEngineState();
            
            return CommandResponse.ok("System Reset Complete", null);
        } catch (Exception e) {
            return CommandResponse.error("Reset failed: " + e.getMessage());
        }
    }

    private void reloadEngineState() {
        Map<UUID, Account> accountMap = new HashMap<>();
        try {
            jdbcTemplate.query("SELECT * FROM \"accounts\"", rs -> {
                UUID id = UUID.fromString(rs.getString("id"));
                BigDecimal bal = rs.getBigDecimal("balance");
                AccountStatus status = AccountStatus.valueOf(rs.getString("status"));
                accountMap.put(id, new Account(id, bal, status));
            });
            AccountState.INSTANCE.loadAll(accountMap);
        } catch (Exception e) {
            // Silent error handling as required
        }
    }
}
