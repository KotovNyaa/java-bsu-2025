package com.bank.api.service;

import com.bank.api.dto.CommandResponse;
import com.bank.core.command.TransactionCommand;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

public class ApiTransactionService {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ApiTransactionService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public CommandResponse deposit(UUID accountId, BigDecimal amount) {
        UUID idempotencyKey = UUID.randomUUID();
        TransactionCommand cmd = TransactionCommand.createDepositCommand(idempotencyKey, accountId, amount);
        return enqueueCommand(cmd, idempotencyKey);
    }

    public CommandResponse withdraw(UUID accountId, BigDecimal amount) {
        UUID idempotencyKey = UUID.randomUUID();
        TransactionCommand cmd = TransactionCommand.createWithdrawCommand(idempotencyKey, accountId, amount);
        return enqueueCommand(cmd, idempotencyKey);
    }

    public CommandResponse transfer(UUID fromAccountId, UUID toAccountId, BigDecimal amount) {
        UUID idempotencyKey = UUID.randomUUID();
        TransactionCommand cmd = TransactionCommand.createTransferCommand(idempotencyKey, fromAccountId, toAccountId, amount);
        return enqueueCommand(cmd, idempotencyKey);
    }

    public CommandResponse freeze(UUID accountId) {
        UUID idempotencyKey = UUID.randomUUID();
        TransactionCommand cmd = TransactionCommand.createFreezeCommand(idempotencyKey, accountId);
        return enqueueCommand(cmd, idempotencyKey);
    }

    public CommandResponse unfreeze(UUID accountId) {
        UUID idempotencyKey = UUID.randomUUID();
        TransactionCommand cmd = TransactionCommand.createUnfreezeCommand(idempotencyKey, accountId);
        return enqueueCommand(cmd, idempotencyKey);
    }

    public CommandResponse close(UUID accountId) {
        UUID idempotencyKey = UUID.randomUUID();
        TransactionCommand cmd = TransactionCommand.createCloseCommand(idempotencyKey, accountId);
        return enqueueCommand(cmd, idempotencyKey);
    }

    private CommandResponse enqueueCommand(TransactionCommand cmd, UUID idempotencyKey) {
        try {
            String payload = objectMapper.writeValueAsString(cmd);

            String sql = "INSERT INTO \"transaction_outbox\" " +
                    "(\"idempotency_key\", \"transaction_id\", \"payload\", \"status\", \"created_at\") " +
                    "VALUES (?, ?, ?, 'PENDING', ?)";

            jdbcTemplate.update(sql,
                    cmd.getIdempotencyKey(),
                    cmd.getTransactionId(),
                    payload,
                    Timestamp.from(Instant.now()));

            return CommandResponse.ok("Task accepted", idempotencyKey);
        } catch (Exception e) {
            return CommandResponse.error("Failed to enqueue task: " + e.getMessage());
        }
    }
}