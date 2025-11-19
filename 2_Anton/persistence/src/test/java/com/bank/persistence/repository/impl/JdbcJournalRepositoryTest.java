package com.bank.persistence.repository.impl;

import com.bank.core.command.ActionType;
import com.bank.core.command.TransactionCommand;
import com.bank.persistence.exception.DataAccessException;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcJournalRepositoryTest {

    private EmbeddedDatabase dataSource;
    private JdbcTemplate jdbcTemplate;
    private JdbcJournalRepository journalRepository;

    @BeforeAll
    void setupDatabase() {
        this.dataSource = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .setName("journaldb_test;MODE=PostgreSQL;DATABASE_TO_UPPER=false")
                .addScript("classpath:schema.sql")
                .build();
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.journalRepository = new JdbcJournalRepository(dataSource);
    }

    @AfterAll
    void shutdownDatabase() {
        if (this.dataSource != null) {
            this.dataSource.shutdown();
        }
    }

    @BeforeEach
    void cleanTables() {
        jdbcTemplate.execute("TRUNCATE TABLE \"transaction_journal\"");
    }

    @Test
    void log_should_insert_correct_record_for_transfer() {
        UUID fromId = UUID.randomUUID();
        UUID toId = UUID.randomUUID();
        TransactionCommand command = TransactionCommand.createTransferCommand(UUID.randomUUID(), fromId, toId,
                new BigDecimal("99.99"));

        journalRepository.log(command);

        jdbcTemplate.query("SELECT * FROM \"transaction_journal\" WHERE \"transaction_id\" = ?", rs -> {
            assertThat(rs.getObject("account_id_from", UUID.class)).isEqualTo(fromId);
            assertThat(rs.getObject("account_id_to", UUID.class)).isEqualTo(toId);
            assertThat(rs.getString("command_type")).isEqualTo("TRANSFER");
            assertThat(rs.getBigDecimal("amount")).isEqualByComparingTo("99.99");
        }, command.getTransactionId());
    }

    @Test
    void log_should_handle_null_account_id_to() {
        TransactionCommand command = TransactionCommand.createDepositCommand(UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("500"));

        journalRepository.log(command);

        UUID accountIdTo = jdbcTemplate.queryForObject(
                "SELECT \"account_id_to\" FROM \"transaction_journal\" WHERE \"transaction_id\" = ?",
                UUID.class,
                command.getTransactionId());
        assertThat(accountIdTo).isNull();
    }

    @Test
    void log_should_throw_DataAccessException_on_sql_error() throws SQLException {
        DataSource failingDataSource = Mockito.mock(DataSource.class);
        when(failingDataSource.getConnection()).thenThrow(new SQLException("Simulated database connection error"));
        JdbcJournalRepository failingRepository = new JdbcJournalRepository(failingDataSource);
        TransactionCommand command = TransactionCommand.createDepositCommand(UUID.randomUUID(), UUID.randomUUID(),
                BigDecimal.TEN);

        assertThrows(DataAccessException.class, () -> {
            failingRepository.log(command);
        }, "A database failure should be wrapped in a DataAccessException");
    }

    @Test
    void should_load_all_journal_entries_in_chronological_order() throws InterruptedException {
        UUID account1 = UUID.randomUUID();
        UUID account2 = UUID.randomUUID();

        TransactionCommand depositCommand = TransactionCommand.createDepositCommand(UUID.randomUUID(), account1,
                new BigDecimal("500.00"));
        Thread.sleep(10);
        TransactionCommand transferCommand = TransactionCommand.createTransferCommand(UUID.randomUUID(), account1,
                account2, new BigDecimal("150.00"));

        journalRepository.log(depositCommand);
        journalRepository.log(transferCommand);

        List<TransactionCommand> loadedCommands = journalRepository.loadAllJournalEntries();

        assertThat(loadedCommands).isNotNull().hasSize(2);

        TransactionCommand loadedDeposit = loadedCommands.get(0);
        assertThat(loadedDeposit.getTransactionId()).isEqualTo(depositCommand.getTransactionId());
        assertThat(loadedDeposit.getActionType()).isEqualTo(ActionType.DEPOSIT);
        assertThat(loadedDeposit.getAccountId()).isEqualTo(account1);
        assertThat(loadedDeposit.getAmount()).isEqualByComparingTo("500.00");

        TransactionCommand loadedTransfer = loadedCommands.get(1);
        assertThat(loadedTransfer.getTransactionId()).isEqualTo(transferCommand.getTransactionId());
        assertThat(loadedTransfer.getActionType()).isEqualTo(ActionType.TRANSFER);
        assertThat(loadedTransfer.getAccountId()).isEqualTo(account1);
        assertThat(loadedTransfer.getTargetAccountId()).isEqualTo(account2);
        assertThat(loadedTransfer.getAmount()).isEqualByComparingTo("150.00");
    }
}
