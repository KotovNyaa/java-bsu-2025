package com.bank.application.service.impl;

import com.bank.application.port.out.TransactionalOutboxRepository;
import com.bank.core.command.TransactionCommand;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * JDBC-реализация репозитория Outbox с поддержкой DLQ и счетчика ошибок
 */

public class JdbcTransactionalOutboxRepository implements TransactionalOutboxRepository {
    private static final Logger log = LoggerFactory.getLogger(JdbcTransactionalOutboxRepository.class);
    private final DataSource dataSource;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JdbcTransactionalOutboxRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public boolean save(TransactionCommand command) {
        final String idempotencySql = "INSERT INTO idempotency_keys (key, created_at) VALUES (?, ?) ON CONFLICT (key) DO NOTHING";
        final String outboxSql = "INSERT INTO transaction_outbox (id, idempotency_key, payload, created_at) VALUES (?, ?, ?, ?)";

        try (Connection conn = dataSource.getConnection()) {
            try {
                conn.setAutoCommit(false);
                try (PreparedStatement idempotencyStmt = conn.prepareStatement(idempotencySql)) {
                    idempotencyStmt.setObject(1, command.getIdempotencyKey());
                    idempotencyStmt.setTimestamp(2, Timestamp.from(Instant.now()));
                    if (idempotencyStmt.executeUpdate() == 0) {
                        conn.rollback();
                        return false;
                    }
                }
                try (PreparedStatement outboxStmt = conn.prepareStatement(outboxSql)) {
                    outboxStmt.setObject(1, command.getTransactionId());
                    outboxStmt.setObject(2, command.getIdempotencyKey());
                    outboxStmt.setString(3, toJson(command));
                    outboxStmt.setTimestamp(4, Timestamp.from(Instant.now()));
                    outboxStmt.executeUpdate();
                }
                conn.commit();
                return true;
            } catch (SQLException e) {
                log.error("Transaction failed for idempotency key {}. Rolling back.", command.getIdempotencyKey(), e);
                conn.rollback();
                throw new RuntimeException("Failed to save transaction to outbox.", e);
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Database connection error.", ex);
        }
    }

    @Override
    public List<TransactionCommand> fetchAndLockUnprocessed(int batchSize) {
        final String sql = "SELECT payload FROM transaction_outbox ORDER BY created_at ASC LIMIT ? FOR UPDATE SKIP LOCKED";
        List<TransactionCommand> commands = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, batchSize);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    commands.add(fromJson(rs.getString("payload")));
                }
            }
        } catch (Exception e) {
            log.error("Failed to fetch commands from outbox.", e);
        }
        return commands;
    }

    @Override
    public void markAsProcessed(TransactionCommand command) {
        final String sql = "DELETE FROM transaction_outbox WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, command.getTransactionId());
            stmt.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to mark command {} as processed.", command.getTransactionId(), e);
        }
    }
    
    @Override
    public void moveToDlq(TransactionCommand command, String reason) {
        final String deleteSql = "DELETE FROM transaction_outbox WHERE id = ?";
        final String insertSql = "INSERT INTO transaction_outbox_dlq (id, payload, reason, moved_at) VALUES (?, ?, ?, ?)";
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement deleteStmt = conn.prepareStatement(deleteSql)) {
                deleteStmt.setObject(1, command.getTransactionId());
                deleteStmt.executeUpdate();
            }
            try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                insertStmt.setObject(1, command.getTransactionId());
                insertStmt.setString(2, toJson(command));
                insertStmt.setString(3, reason);
                insertStmt.setTimestamp(4, Timestamp.from(Instant.now()));
                insertStmt.executeUpdate();
            }
            conn.commit();
        } catch (SQLException e) {
            log.error("CRITICAL: Failed to move command {} to DLQ.", command.getTransactionId(), e);
        }
    }
    
    @Override
    public int getFailureCount(UUID transactionId) {
        final String sql = "SELECT failure_count FROM transaction_outbox WHERE id = ?";
        try (Connection conn = dataSource.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, transactionId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt("failure_count") : 0;
            }
        } catch (SQLException e) {
            return 0;
        }
    }

    @Override
    public void incrementFailureCount(UUID transactionId) {
        final String sql = "UPDATE transaction_outbox SET failure_count = failure_count + 1 WHERE id = ?";
        try (Connection conn = dataSource.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, transactionId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to increment failure count for tx {}", transactionId, e);
        }
    }
    
    private String toJson(TransactionCommand command) {
        try { return objectMapper.writeValueAsString(command); } catch (Exception e) { throw new RuntimeException(e); }
    }
    private TransactionCommand fromJson(String json) {
        try { return objectMapper.readValue(json, TransactionCommand.class); } catch (Exception e) { throw new RuntimeException(e); }
    }
}
