package com.bank.persistence.repository.impl;

import com.bank.core.command.TransactionCommand;
import com.bank.core.port.out.JournalingService;
import com.bank.persistence.exception.DataAccessException;
import com.bank.core.command.ActionType;
import java.util.ArrayList;  
import java.util.List; 
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

/**
 * JournalRepository для регестрации команд транзакции в БД
 */

public final class JdbcJournalRepository implements JournalingService {

    private final DataSource dataSource;

    public JdbcJournalRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void log(TransactionCommand command) {
        final String sql = "INSERT INTO transaction_journal " +
                           "(idempotency_key, transaction_id, timestamp, command_type, account_id_from, account_id_to, amount) " +
                           "VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, command.getIdempotencyKey());
            stmt.setObject(2, command.getTransactionId());
            stmt.setTimestamp(3, Timestamp.from(Instant.ofEpochMilli(command.getTimestamp())));
            stmt.setString(4, command.getActionType().name());
            stmt.setObject(5, command.getAccountId());
            stmt.setObject(6, command.getTargetAccountId());
            stmt.setBigDecimal(7, command.getAmount());

            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Fatal: Failed to write command to journal. Idempotency Key: " + command.getIdempotencyKey(), e);
        }
    }

    public List<TransactionCommand> loadAllJournalEntries() {
    final String SQL = "SELECT * FROM transaction_journal ORDER BY timestamp ASC";
    List<TransactionCommand> commands = new ArrayList<>();

    try (Connection conn = dataSource.getConnection();
         Statement stmt = conn.createStatement();
         ResultSet rs = stmt.executeQuery(SQL)) {

        while (rs.next()) {
            UUID transactionId = rs.getObject("transaction_id", UUID.class);
            UUID idempotencyKey = rs.getObject("idempotency_key", UUID.class); 
            ActionType type = ActionType.valueOf(rs.getString("command_type"));
            UUID accountId = rs.getObject("account_id_from", UUID.class);
            UUID targetAccountId = rs.getObject("account_id_to", UUID.class);
            BigDecimal amount = rs.getBigDecimal("amount");

            TransactionCommand command = new TransactionCommand(
                transactionId,
                idempotencyKey,
                accountId,
                type,
                amount,
                targetAccountId
            );
            commands.add(command);
        }
    } catch (SQLException e) {
        throw new DataAccessException("Failed to load transaction journal", e);
    }
    return commands;
}}
