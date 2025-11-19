package com.bank.application;

import com.bank.application.service.TransactionService;
import com.bank.core.state.AccountState;
import com.bank.domain.Account;
import com.bank.domain.AccountStatus;

import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AccountLifecycleTest {

    private EmbeddedDatabase dataSource;
    private JdbcTemplate jdbcTemplate;
    private BankApplication bankApplication;
    private TransactionService transactionService;

    @BeforeAll
    void setupDatabase() {
        this.dataSource = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .setName("testdb_lifecycle;MODE=PostgreSQL;DATABASE_TO_UPPER=false")
                .addScript("classpath:schema.sql")
                .build();
        this.jdbcTemplate = new JdbcTemplate(this.dataSource);
    }

    @AfterAll
    void shutdownDatabase() {
        this.dataSource.shutdown();
    }

    @BeforeEach
    void setUp() {
        cleanAllTables();
        AccountState.INSTANCE.loadAll(java.util.Collections.emptyMap());

        this.bankApplication = new BankApplication(this.dataSource);
        this.bankApplication.start();
        this.transactionService = this.bankApplication.getTransactionService();
    }

    @AfterEach
    void tearDown() {
        if (this.bankApplication != null) {
            this.bankApplication.stop();
        }
    }

    @Test
    void should_handleFreezeAndUnfreezeCorrectly() {
        BigDecimal initialBalance = new BigDecimal("1000.00");
        UUID accountId = createAccountInDb(initialBalance);

        UUID freezeKey = UUID.randomUUID();
        transactionService.freezeAccount(freezeKey, accountId);

        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(getAccountStatus(accountId)).isEqualTo("FROZEN"));

        UUID withdrawFailKey = UUID.randomUUID();
        transactionService.withdraw(withdrawFailKey, accountId, new BigDecimal("100.00"));

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(getRowCount("\"transaction_outbox_dlq\"", withdrawFailKey)).isOne();
            assertThat(getBalanceFromDb(accountId)).isEqualByComparingTo(initialBalance);
        });

        UUID unfreezeKey = UUID.randomUUID();
        transactionService.unfreezeAccount(unfreezeKey, accountId);

        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(getAccountStatus(accountId)).isEqualTo("ACTIVE"));

        UUID withdrawSuccessKey = UUID.randomUUID();
        transactionService.withdraw(withdrawSuccessKey, accountId, new BigDecimal("100.00"));

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(getBalanceFromDb(accountId)).isEqualByComparingTo("900.00");
            assertThat(getRowCount("\"transaction_outbox\"", withdrawSuccessKey)).isZero();
        });
    }

    @Test
    void should_handleAccountClosure() {
        UUID accountId = createAccountInDb(new BigDecimal("0.00"));

        UUID closeKey = UUID.randomUUID();
        transactionService.closeAccount(closeKey, accountId);

        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(getAccountStatus(accountId)).isEqualTo("CLOSED"));

        UUID depositKey = UUID.randomUUID();
        transactionService.deposit(depositKey, accountId, new BigDecimal("500.00"));

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(getRowCount("\"transaction_outbox_dlq\"", depositKey)).isOne();
            assertThat(getBalanceFromDb(accountId)).isEqualByComparingTo("0.00");
        });
    }

    private void cleanAllTables() {
        jdbcTemplate.execute("TRUNCATE TABLE \"processed_transactions\" RESTART IDENTITY");
        jdbcTemplate.execute("TRUNCATE TABLE \"transaction_journal\" RESTART IDENTITY");
        jdbcTemplate.execute("TRUNCATE TABLE \"transaction_outbox_dlq\" RESTART IDENTITY");
        jdbcTemplate.execute("TRUNCATE TABLE \"transaction_outbox\" RESTART IDENTITY");
        jdbcTemplate.execute("TRUNCATE TABLE \"idempotency_keys\" RESTART IDENTITY");
        jdbcTemplate.execute("TRUNCATE TABLE \"accounts\" RESTART IDENTITY");
    }

    private UUID createAccountInDb(BigDecimal initialBalance) {
        UUID accountId = UUID.randomUUID();

        jdbcTemplate.update("INSERT INTO \"accounts\" (\"id\", \"balance\", \"status\") VALUES (?, ?, 'ACTIVE')",
                accountId, initialBalance);

        Account account = new Account(accountId, initialBalance, AccountStatus.ACTIVE);
        AccountState.INSTANCE.createOrUpdateAccount(account);

        return accountId;
    }

    private BigDecimal getBalanceFromDb(UUID accountId) {
        return jdbcTemplate.queryForObject("SELECT \"balance\" FROM \"accounts\" WHERE \"id\" = ?", BigDecimal.class,
                accountId);
    }

    private String getAccountStatus(UUID accountId) {
        return jdbcTemplate.queryForObject("SELECT \"status\" FROM \"accounts\" WHERE \"id\" = ?", String.class,
                accountId);
    }

    private int getRowCount(String tableName, UUID key) {
        if (tableName.contains("dlq")) {
            return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Integer.class);
        }
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName + " WHERE \"idempotency_key\" = ?",
                Integer.class, key);
    }
}
