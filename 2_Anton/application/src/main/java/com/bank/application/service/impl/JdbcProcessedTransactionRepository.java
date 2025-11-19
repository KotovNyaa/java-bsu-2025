package com.bank.application.service.impl;

import com.bank.application.port.out.ProcessedTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * JDBC-реализация для персистентного хранения ID обработанных транзакций
 */

public class JdbcProcessedTransactionRepository implements ProcessedTransactionRepository {

    private static final Logger log = LoggerFactory.getLogger(JdbcProcessedTransactionRepository.class);
    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    public JdbcProcessedTransactionRepository(DataSource dataSource) {
        this.dataSource = dataSource;
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Override
    public boolean isProcessedAndMark(UUID idempotencyKey) {
        final String sql = "INSERT INTO \"processed_transactions\" (\"idempotency_key\", \"processed_at\") VALUES (?, ?)";
        try (Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, idempotencyKey);
            stmt.setTimestamp(2, Timestamp.from(Instant.now()));
            stmt.executeUpdate();

            return true;

        } catch (SQLException e) {
            if ("23505".equals(e.getSQLState())) {
                log.trace("Duplicate transaction detected with idempotency key: {}", idempotencyKey);
                return false;
            }
            log.error("Database error while marking transaction as processed for ID {}.", idempotencyKey, e);
            return false;
        }
    }

    @Override
    public Set<UUID> loadAllProcessedKeys() {
        final String sql = "SELECT \"idempotency_key\" FROM \"processed_transactions\"";
        try {
            return new HashSet<>(jdbcTemplate.queryForList(sql, UUID.class));
        } catch (DataAccessException e) {
            log.error("Failed to load processed transaction keys from database. The idempotency cache will be empty.",
                    e);
            return new HashSet<>();
        }
    }
}
