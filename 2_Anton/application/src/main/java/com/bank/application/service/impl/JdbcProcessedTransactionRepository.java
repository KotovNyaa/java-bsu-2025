package com.bank.application.service.impl;

import com.bank.application.port.out.ProcessedTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

/**
 * JDBC-реализация для персистентного хранения ID обработанных транзакций
 */

public class JdbcProcessedTransactionRepository implements ProcessedTransactionRepository {

    private static final Logger log = LoggerFactory.getLogger(JdbcProcessedTransactionRepository.class);
    private final DataSource dataSource;

    public JdbcProcessedTransactionRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public boolean isProcessedAndMark(UUID transactionId) {
        final String sql = "INSERT INTO processed_transactions (transaction_id, processed_at) VALUES (?, ?) ON CONFLICT (transaction_id) DO NOTHING";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, transactionId);
            stmt.setTimestamp(2, Timestamp.from(Instant.now()));
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            log.error("Database error during processed transaction check for ID {}. Assuming it was processed to be safe.", transactionId, e);
            return false;
        }
    }
}
