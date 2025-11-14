package com.bank.persistence.repository.impl;

import com.bank.core.command.TransactionCommand;
import com.bank.persistence.exception.DataAccessException;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import com.bank.core.command.ActionType;

import java.util.List;
import javax.sql.DataSource;
import java.io.FileReader;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class JdbcJournalRepositoryTest {

    private JdbcDataSource dataSource;
    private JdbcJournalRepository journalRepository;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:journaldb;DB_CLOSE_DELAY=-1;");
        dataSource.setUser("sa");
        dataSource.setPassword("sa");

        try (Connection conn = dataSource.getConnection()) {
            java.net.URL resource = getClass().getClassLoader().getResource("schema.sql");
            if (resource == null) throw new IllegalStateException("Cannot find schema.sql");
            org.h2.tools.RunScript.execute(conn, new FileReader(resource.getFile()));
        }

        journalRepository = new JdbcJournalRepository(dataSource);
    }

    @Test
    void log_should_insert_correct_record_for_transfer() throws Exception {
        UUID fromId = UUID.randomUUID();
        UUID toId = UUID.randomUUID();
        TransactionCommand command = TransactionCommand.createTransferCommand(fromId, toId, new BigDecimal("99.99"));

        journalRepository.log(command);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT * FROM transaction_journal WHERE transaction_id = ?")) {
            stmt.setObject(1, command.getTransactionId());
            try (ResultSet rs = stmt.executeQuery()) {
                assertTrue(rs.next(), "A record for the transaction should exist in the journal");
                assertEquals(fromId, rs.getObject("account_id_from", UUID.class));
                assertEquals(toId, rs.getObject("account_id_to", UUID.class));
                assertEquals("TRANSFER", rs.getString("command_type"));
                assertEquals(0, new BigDecimal("99.99").compareTo(rs.getBigDecimal("amount")));
            }
        }
    }

    @Test
    void log_should_handle_null_account_id_to() throws Exception {
        TransactionCommand command = TransactionCommand.createDepositCommand(UUID.randomUUID(), new BigDecimal("500"));
        
        journalRepository.log(command);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT account_id_to FROM transaction_journal WHERE transaction_id = ?")) {
            stmt.setObject(1, command.getTransactionId());
            try (ResultSet rs = stmt.executeQuery()) {
                assertTrue(rs.next(), "Record should exist");
                assertNull(rs.getObject("account_id_to"), "account_id_to should be null for deposit");
            }
        }
    }

    @Test
    void log_should_throw_DataAccessException_on_sql_error() throws SQLException {
        DataSource failingDataSource = Mockito.mock(DataSource.class);
        when(failingDataSource.getConnection()).thenThrow(new SQLException("Simulated database connection error"));
        
        JdbcJournalRepository failingRepository = new JdbcJournalRepository(failingDataSource);
        
        TransactionCommand command = TransactionCommand.createDepositCommand(UUID.randomUUID(), BigDecimal.TEN);

        assertThrows(DataAccessException.class, () -> {
            failingRepository.log(command);
        }, "A database failure should be wrapped in a DataAccessException");
    }

    @Test
    void should_load_all_journal_entries_in_chronological_order() throws InterruptedException {
        UUID account1 = UUID.randomUUID();
        UUID account2 = UUID.randomUUID();

        TransactionCommand depositCommand = TransactionCommand.createDepositCommand(account1, new BigDecimal("500.00"));
        Thread.sleep(10);
        TransactionCommand transferCommand = TransactionCommand.createTransferCommand(account1, account2, new BigDecimal("150.00"));

        journalRepository.log(depositCommand);
        journalRepository.log(transferCommand);

        List<TransactionCommand> loadedCommands = journalRepository.loadAllJournalEntries();

        assertNotNull(loadedCommands);
        assertEquals(2, loadedCommands.size(), "Должны быть загружены обе команды");

        TransactionCommand loadedDeposit = loadedCommands.get(0);
        assertEquals(depositCommand.getTransactionId(), loadedDeposit.getTransactionId());
        assertEquals(ActionType.DEPOSIT, loadedDeposit.getActionType());
        assertEquals(account1, loadedDeposit.getAccountId());
        assertEquals(0, new BigDecimal("500.00").compareTo(loadedDeposit.getAmount()));

        TransactionCommand loadedTransfer = loadedCommands.get(1);
        assertEquals(transferCommand.getTransactionId(), loadedTransfer.getTransactionId());
        assertEquals(ActionType.TRANSFER, loadedTransfer.getActionType());
        assertEquals(account1, loadedTransfer.getAccountId());
        assertEquals(account2, loadedTransfer.getTargetAccountId());
        assertEquals(0, new BigDecimal("150.00").compareTo(loadedTransfer.getAmount()));
    }
}
