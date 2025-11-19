package com.bank.persistence.integration;

import com.bank.core.command.TransactionCommand;
import com.bank.domain.Account;
import com.bank.persistence.repository.impl.JdbcAccountRepository;
import com.bank.persistence.repository.impl.JdbcJournalRepository;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CorePersistenceIntegrationTest {

    private EmbeddedDatabase dataSource;
    private JdbcTemplate jdbcTemplate;
    private JdbcAccountRepository accountRepository;
    private JdbcJournalRepository journalRepository;

    @BeforeAll
    void setupDatabase() {
        this.dataSource = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .setName("integrationdb_test;MODE=PostgreSQL;DATABASE_TO_UPPER=false")
                .addScript("classpath:schema.sql")
                .build();
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.accountRepository = new JdbcAccountRepository(dataSource);
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
        jdbcTemplate.execute("TRUNCATE TABLE \"accounts\"");
        jdbcTemplate.execute("TRUNCATE TABLE \"transaction_journal\"");
    }

    @Test
    void should_correctly_restore_state_from_journal_after_simulated_crash() {
        UUID accountId1 = UUID.randomUUID();
        UUID accountId2 = UUID.randomUUID();

        jdbcTemplate.update("INSERT INTO \"accounts\" (\"id\", \"balance\", \"status\") VALUES (?, ?, 'ACTIVE')",
                accountId1, new BigDecimal("1000"));
        jdbcTemplate.update("INSERT INTO \"accounts\" (\"id\", \"balance\", \"status\") VALUES (?, ?, 'ACTIVE')",
                accountId2, new BigDecimal("500"));

        TransactionCommand deposit = TransactionCommand.createDepositCommand(UUID.randomUUID(), accountId1,
                new BigDecimal("200"));
        TransactionCommand withdraw = TransactionCommand.createWithdrawCommand(UUID.randomUUID(), accountId2,
                new BigDecimal("100"));
        TransactionCommand transfer = TransactionCommand.createTransferCommand(UUID.randomUUID(), accountId1,
                accountId2, new BigDecimal("300"));

        journalRepository.log(deposit);
        journalRepository.log(withdraw);
        journalRepository.log(transfer);

        Map<UUID, Account> recoveredState = accountRepository.loadAllAccounts();

        List<TransactionCommand> journalEntries = journalRepository.loadAllJournalEntries();

        for (TransactionCommand command : journalEntries) {
            Account sourceAccount = recoveredState.get(command.getAccountId());
            switch (command.getActionType()) {
                case DEPOSIT:
                    sourceAccount.deposit(command.getAmount());
                    break;
                case WITHDRAW:
                    sourceAccount.withdraw(command.getAmount());
                    break;
                case TRANSFER:
                    Account targetAccount = recoveredState.get(command.getTargetAccountId());
                    sourceAccount.withdraw(command.getAmount());
                    targetAccount.deposit(command.getAmount());
                    break;
            }
        }

        assertThat(recoveredState.get(accountId1).getBalance()).isEqualByComparingTo("900");
        assertThat(recoveredState.get(accountId2).getBalance()).isEqualByComparingTo("700");
    }
}
