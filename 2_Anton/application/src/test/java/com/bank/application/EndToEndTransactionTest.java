package com.bank.application;

import com.bank.application.service.TransactionService;
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
class EndToEndTransactionTest {

    private EmbeddedDatabase dataSource;
    private JdbcTemplate jdbcTemplate;

    private BankApplication bankApplication;
    private TransactionService transactionService;
    private UUID accountId1;

    @BeforeAll
    void setupDatabase() {
        this.dataSource = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .setName("testdb_e2e;MODE=PostgreSQL;DATABASE_TO_UPPER=false")
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

        this.bankApplication = new BankApplication(this.dataSource);
        this.bankApplication.start();
        this.transactionService = this.bankApplication.getTransactionService();

        accountId1 = createAccountInDb(new BigDecimal("1000.00"));

        this.bankApplication.stop();
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
    void should_processDepositTransaction_fullyAndUpdateDatabaseState() {
        UUID idempotencyKey = UUID.randomUUID();
        BigDecimal depositAmount = new BigDecimal("500.25");

        transactionService.deposit(idempotencyKey, accountId1, depositAmount);

        await().atMost(5, TimeUnit.SECONDS).pollInterval(100, TimeUnit.MILLISECONDS).untilAsserted(() -> {
            BigDecimal finalBalance = getBalanceFromDb(accountId1);
            assertThat(finalBalance).isEqualByComparingTo("1500.25");
            assertThat(getRowCount("\"transaction_outbox\"", "\"idempotency_key\"", idempotencyKey)).isZero();
            assertThat(getRowCount("\"transaction_journal\"", "\"idempotency_key\"", idempotencyKey)).isOne();
        });
    }

    @Test
    void should_notChangeBalance_andMoveToDlq_whenWithdrawingWithInsufficientFunds() {
        UUID idempotencyKey = UUID.randomUUID();
        BigDecimal withdrawAmount = new BigDecimal("2000.00");

        transactionService.withdraw(idempotencyKey, accountId1, withdrawAmount);

        await().atMost(5, TimeUnit.SECONDS).pollInterval(100, TimeUnit.MILLISECONDS).untilAsserted(() -> {
            BigDecimal finalBalance = getBalanceFromDb(accountId1);
            assertThat(finalBalance).isEqualByComparingTo("1000.00");
            assertThat(getRowCount("\"transaction_outbox\"", "\"idempotency_key\"", idempotencyKey)).isZero();
            assertThat(getRowCount("\"transaction_outbox_dlq\"", null, null)).isOne();
        });
    }

    @Test
    void should_executeTransactionOnlyOnce_whenPublishedMultipleTimes() {
        UUID idempotencyKey = UUID.randomUUID();
        BigDecimal depositAmount = new BigDecimal("100.00");

        transactionService.deposit(idempotencyKey, accountId1, depositAmount);
        transactionService.deposit(idempotencyKey, accountId1, depositAmount);

        await().atMost(5, TimeUnit.SECONDS).pollInterval(100, TimeUnit.MILLISECONDS).untilAsserted(() -> {
            BigDecimal finalBalance = getBalanceFromDb(accountId1);
            assertThat(finalBalance).isEqualByComparingTo("1100.00");
            assertThat(getRowCount("\"transaction_outbox\"", "\"idempotency_key\"", idempotencyKey)).isZero();
            assertThat(getRowCount("\"transaction_journal\"", "\"idempotency_key\"", idempotencyKey)).isOne();
        });
    }

    private void cleanAllTables() {
        jdbcTemplate.execute("TRUNCATE TABLE \"accounts\" RESTART IDENTITY");
        jdbcTemplate.execute("TRUNCATE TABLE \"transaction_outbox\" RESTART IDENTITY");
        jdbcTemplate.execute("TRUNCATE TABLE \"transaction_outbox_dlq\" RESTART IDENTITY");
        jdbcTemplate.execute("TRUNCATE TABLE \"transaction_journal\" RESTART IDENTITY");
        jdbcTemplate.execute("TRUNCATE TABLE \"processed_transactions\" RESTART IDENTITY");
        jdbcTemplate.execute("TRUNCATE TABLE \"idempotency_keys\" RESTART IDENTITY");
    }

    private UUID createAccountInDb(BigDecimal initialBalance) {
        UUID accountId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO \"accounts\" (\"id\", \"balance\", \"status\") VALUES (?, ?, 'ACTIVE')",
                accountId, initialBalance);
        return accountId;
    }

    private BigDecimal getBalanceFromDb(UUID accountId) {
        return jdbcTemplate.queryForObject("SELECT \"balance\" FROM \"accounts\" WHERE \"id\" = ?", BigDecimal.class,
                accountId);
    }

    private int getRowCount(String tableName, String keyColumn, Object keyValue) {
        String sql = "SELECT COUNT(*) FROM " + tableName;
        if (keyColumn != null) {
            sql += " WHERE " + keyColumn + " = ?";
            return jdbcTemplate.queryForObject(sql, Integer.class, keyValue);
        }
        return jdbcTemplate.queryForObject(sql, Integer.class);
    }
}
