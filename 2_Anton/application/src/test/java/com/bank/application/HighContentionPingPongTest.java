package com.bank.application;

import com.bank.application.service.TransactionService;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HighContentionPingPongTest {

    private EmbeddedDatabase dataSource;
    private JdbcTemplate jdbcTemplate;
    private BankApplication bankApplication;

    private static final int TRANSFERS_ONE_WAY = 5000;
    private static final int TOTAL_TRANSACTIONS = TRANSFERS_ONE_WAY * 2;
    private static final int PRODUCER_THREADS = 8;
    private static final BigDecimal INITIAL_BALANCE = new BigDecimal("1000000.00");

    @BeforeAll
    void setupDatabase() {
        this.dataSource = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .setName("testdb_pingpong;MODE=PostgreSQL;DATABASE_TO_UPPER=false")
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
    }

    @AfterEach
    void tearDown() {
        if (this.bankApplication != null) {
            this.bankApplication.stop();
        }
    }

    @Test
    void should_maintainCorrectBalances_underHighContention() throws InterruptedException {
        UUID accountIdA = createAccountInDb(INITIAL_BALANCE);
        UUID accountIdB = createAccountInDb(INITIAL_BALANCE);

        this.bankApplication.stop();
        this.bankApplication = new BankApplication(this.dataSource);
        this.bankApplication.start();
        TransactionService transactionService = this.bankApplication.getTransactionService();

        BigDecimal totalInitialBalance = getTotalBalanceFromDb();
        System.out.printf("Initial balances: A=%s, B=%s. Total=%s. Starting %d transactions...%n",
                INITIAL_BALANCE, INITIAL_BALANCE, totalInitialBalance, TOTAL_TRANSACTIONS);

        List<Runnable> tasks = new ArrayList<>(TOTAL_TRANSACTIONS);
        for (int i = 0; i < TRANSFERS_ONE_WAY; i++) {
            tasks.add(() -> transactionService.transfer(UUID.randomUUID(), accountIdA, accountIdB, BigDecimal.ONE));
            tasks.add(() -> transactionService.transfer(UUID.randomUUID(), accountIdB, accountIdA, BigDecimal.ONE));
        }
        Collections.shuffle(tasks);

        ExecutorService executor = Executors.newFixedThreadPool(PRODUCER_THREADS);
        CountDownLatch latch = new CountDownLatch(TOTAL_TRANSACTIONS);

        tasks.forEach(task -> executor.submit(() -> {
            try {
                task.run();
            } finally {
                latch.countDown();
            }
        }));

        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();
        System.out.println("All transaction requests have been sent.");

        System.out.println("Waiting for outbox to become empty...");
        await().atMost(40, TimeUnit.SECONDS).pollInterval(500, TimeUnit.MILLISECONDS)
                .until(() -> getRowCount("\"transaction_outbox\"") == 0);
        System.out.println("Outbox is empty. Verifying final state.");

        BigDecimal finalBalanceA = getBalanceFromDb(accountIdA);
        BigDecimal finalBalanceB = getBalanceFromDb(accountIdB);
        BigDecimal totalFinalBalance = getTotalBalanceFromDb();

        assertThat(finalBalanceA)
                .withFailMessage("Итоговый баланс счета А не равен начальному! Было: %s, стало: %s",
                        INITIAL_BALANCE, finalBalanceA)
                .isEqualByComparingTo(INITIAL_BALANCE);

        assertThat(finalBalanceB)
                .withFailMessage("Итоговый баланс счета Б не равен начальному! Было: %s, стало: %s",
                        INITIAL_BALANCE, finalBalanceB)
                .isEqualByComparingTo(INITIAL_BALANCE);

        assertThat(totalFinalBalance)
                .isEqualByComparingTo(totalInitialBalance);
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

    private BigDecimal getTotalBalanceFromDb() {
        return jdbcTemplate.queryForObject("SELECT SUM(\"balance\") FROM \"accounts\"", BigDecimal.class);
    }

    private int getRowCount(String tableName) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Integer.class);
    }

    private void cleanAllTables() {
        jdbcTemplate.execute("TRUNCATE TABLE \"processed_transactions\" RESTART IDENTITY");
        jdbcTemplate.execute("TRUNCATE TABLE \"transaction_journal\" RESTART IDENTITY");
        jdbcTemplate.execute("TRUNCATE TABLE \"transaction_outbox_dlq\" RESTART IDENTITY");
        jdbcTemplate.execute("TRUNCATE TABLE \"transaction_outbox\" RESTART IDENTITY");
        jdbcTemplate.execute("TRUNCATE TABLE \"idempotency_keys\" RESTART IDENTITY");
        jdbcTemplate.execute("TRUNCATE TABLE \"accounts\" RESTART IDENTITY");
    }
}
