package com.bank.application.service.impl;

import com.bank.application.port.out.TransactionalOutboxRepository;
import com.bank.core.command.TransactionCommand;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * JDBC-реализация репозитория Outbox с поддержкой DLQ и счетчика ошибок
 */

public class JdbcTransactionalOutboxRepository implements TransactionalOutboxRepository {
    private static final Logger log = LoggerFactory.getLogger(JdbcTransactionalOutboxRepository.class);

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;

    public JdbcTransactionalOutboxRepository(DataSource dataSource, TransactionTemplate transactionTemplate) {
        this.dataSource = dataSource;
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.transactionTemplate = transactionTemplate;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    @Override
    public boolean save(TransactionCommand command) {
        final String idempotencySql = "MERGE INTO \"idempotency_keys\" (\"key\", \"created_at\") KEY(\"key\") VALUES (?, ?)";
        final String outboxSql = "INSERT INTO \"transaction_outbox\" (\"idempotency_key\", \"transaction_id\", \"payload\", \"created_at\") VALUES (?, ?, ?, ?)";

        try (Connection conn = dataSource.getConnection()) {
            try {
                conn.setAutoCommit(false);

                try (PreparedStatement idempotencyStmt = conn.prepareStatement(idempotencySql)) {
                    idempotencyStmt.setObject(1, command.getIdempotencyKey());
                    idempotencyStmt.setTimestamp(2, Timestamp.from(Instant.now()));
                    if (idempotencyStmt.executeUpdate() == 0) {
                        log.warn("Idempotency key {} already exists. Assuming duplicate submission.",
                                command.getIdempotencyKey());
                        conn.rollback();
                        return false;
                    }
                }

                try (PreparedStatement outboxStmt = conn.prepareStatement(outboxSql)) {
                    outboxStmt.setObject(1, command.getIdempotencyKey());
                    outboxStmt.setObject(2, command.getTransactionId());
                    outboxStmt.setString(3, toJson(command));
                    outboxStmt.setTimestamp(4, Timestamp.from(Instant.now()));
                    outboxStmt.executeUpdate();
                }

                conn.commit();
                return true;

            } catch (SQLException e) {
                conn.rollback();
                if ("23505".equals(e.getSQLState())) {
                    log.warn("Attempted to save a duplicate transaction to outbox with key: {}. Ignoring.",
                            command.getIdempotencyKey());
                    return false;
                } else {
                    log.error("Transaction failed for idempotency key {}. SQLState: {}, ErrorCode: {}",
                            command.getIdempotencyKey(), e.getSQLState(), e.getErrorCode(), e);
                    throw new RuntimeException("Failed to save transaction to outbox due to a database error.", e);
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Database connection error.", ex);
        }
    }

    @Override
    public List<TransactionCommand> fetchAndLockUnprocessed(int batchSize) {
        return transactionTemplate.execute(status -> {
            final String findSql = "SELECT \"transaction_id\" FROM \"transaction_outbox\" WHERE \"status\" = 'PENDING' ORDER BY \"created_at\" ASC LIMIT ? FOR UPDATE SKIP LOCKED";
            List<UUID> candidateIds = jdbcTemplate.queryForList(findSql, UUID.class, batchSize);

            if (candidateIds.isEmpty()) {
                return List.of();
            }

            final String updateSql = "UPDATE \"transaction_outbox\" SET \"status\" = 'PROCESSING' WHERE \"transaction_id\" = ?";
            jdbcTemplate.batchUpdate(updateSql, candidateIds, 100, (ps, id) -> ps.setObject(1, id));

            String idsForInClause = candidateIds.stream()
                    .map(id -> "'" + id.toString() + "'")
                    .collect(Collectors.joining(","));

            final String fetchPayloadsSql = "SELECT \"payload\" FROM \"transaction_outbox\" WHERE \"transaction_id\" IN ("
                    + idsForInClause + ")";

            return jdbcTemplate.query(fetchPayloadsSql, (rs, rowNum) -> fromJson(rs.getString("payload")));
        });
    }

    @Override
    public void markAsProcessed(TransactionCommand command) {
        final String sql = "DELETE FROM \"transaction_outbox\" WHERE \"idempotency_key\" = ?";
        try (Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, command.getIdempotencyKey());
            stmt.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to mark command with idempotency key {} as processed.", command.getIdempotencyKey(), e);
        }
    }

    @Override
    public void moveToDlq(TransactionCommand command, String reason) {
        final String deleteSql = "DELETE FROM \"transaction_outbox\" WHERE \"idempotency_key\" = ?";
        final String insertSql = "INSERT INTO \"transaction_outbox_dlq\" (\"id\", \"payload\", \"reason\", \"moved_at\") VALUES (?, ?, ?, ?)";

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);

            try (PreparedStatement deleteStmt = conn.prepareStatement(deleteSql)) {
                deleteStmt.setObject(1, command.getIdempotencyKey());
                int deletedRows = deleteStmt.executeUpdate();
                if (deletedRows == 0) {
                    log.warn(
                            "Could not find transaction with key {} in outbox to move to DLQ. It might have been processed already.",
                            command.getIdempotencyKey());
                    conn.rollback();
                    return;
                }
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
            try (Connection c = dataSource.getConnection()) {
                if (c != null && !c.getAutoCommit()) {
                    c.rollback();
                }
            } catch (SQLException ex) {
                log.error("Failed to rollback DLQ transaction.", ex);
            }
        }
    }

    @Override
    public int getFailureCount(UUID transactionId) {
        final String sql = "SELECT failure_count FROM transaction_outbox WHERE transaction_id = ?"; // Ваш SQL
        try (Connection conn = dataSource.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, transactionId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt("failure_count") : 0;
            }
        } catch (SQLException e) {
            log.warn("Could not get failure count for transactionId {}", transactionId, e);
            return 0;
        }
    }

    @Override
    public void incrementFailureCount(UUID idempotencyKey) {
        final String sql = "UPDATE \"transaction_outbox\" SET failure_count = failure_count + 1 WHERE \"idempotency_key\" = ?";
        try (Connection conn = dataSource.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, idempotencyKey);
            stmt.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to increment failure count for tx with key {}", idempotencyKey, e);
        }
    }

    private String toJson(TransactionCommand command) {
        try {
            return objectMapper.writeValueAsString(command);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize command to JSON", e);
        }
    }

    private TransactionCommand fromJson(String json) {
        try {
            return objectMapper.readValue(json, TransactionCommand.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize command from JSON", e);
        }
    }

    @Override
    public void resetProcessingToPending() {
        final String sql = "UPDATE \"transaction_outbox\" SET \"status\" = 'PENDING' WHERE \"status\" = 'PROCESSING'";
        int count = jdbcTemplate.update(sql);
        if (count > 0) {
            log.info("Crash Recovery: Reset {} stuck transactions from PROCESSING to PENDING.", count);
        }
    }
}
