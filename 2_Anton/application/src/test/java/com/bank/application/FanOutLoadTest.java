package com.bank.application;

import com.bank.application.service.TransactionService;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FanOutLoadTest {

    private EmbeddedDatabase dataSource;
    private JdbcTemplate jdbcTemplate;
    private BankApplication bankApplication;

    private static final int SENDER_COUNT = 20;
    private static final int RECEIVER_COUNT = 20;
    private static final int TRANSACTIONS_PER_SENDER = 500;
    private static final int TOTAL_TRANSACTIONS = SENDER_COUNT * TRANSACTIONS_PER_SENDER;
    private static final int PRODUCER_THREADS = 8;

    @BeforeAll
    void setupDatabase() {
        this.dataSource = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .setName("testdb_fanout;MODE=PostgreSQL;DATABASE_TO_UPPER=false")
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
    void should_processAllTransactionsCorrectly_underDistributedLoad() throws InterruptedException {
        BigDecimal senderInitialBalance = new BigDecimal(TRANSACTIONS_PER_SENDER);
        BigDecimal receiverInitialBalance = BigDecimal.ZERO;

        List<UUID> senderIds = createAccountsInDb(SENDER_COUNT, senderInitialBalance);
        List<UUID> receiverIds = createAccountsInDb(RECEIVER_COUNT, receiverInitialBalance);

        this.bankApplication.stop();
        this.bankApplication = new BankApplication(this.dataSource);
        this.bankApplication.start();
        TransactionService transactionService = this.bankApplication.getTransactionService();

        BigDecimal totalInitialBalance = getTotalBalanceFromDb();
        System.out.printf("Total initial balance: %s. Starting %d transactions...%n", totalInitialBalance,
                TOTAL_TRANSACTIONS);

        ExecutorService executor = Executors.newFixedThreadPool(PRODUCER_THREADS);
        CountDownLatch latch = new CountDownLatch(TOTAL_TRANSACTIONS);

        for (int i = 0; i < SENDER_COUNT; i++) {
            UUID senderId = senderIds.get(i);
            for (int j = 0; j < TRANSACTIONS_PER_SENDER; j++) {
                UUID receiverId = receiverIds.get((i + j) % RECEIVER_COUNT);

                executor.submit(() -> {
                    try {
                        transactionService.transfer(UUID.randomUUID(), senderId, receiverId, BigDecimal.ONE);
                    } finally {
                        latch.countDown();
                    }
                });
            }
        }

        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();
        System.out.println("All transaction requests have been sent.");

        System.out.println("Waiting for outbox to become empty...");
        await().atMost(40, TimeUnit.SECONDS).pollInterval(500, TimeUnit.MILLISECONDS)
                .until(() -> getRowCount("\"transaction_outbox\"") == 0);
        System.out.println("Outbox is empty. Verifying final state.");

        BigDecimal totalFinalBalance = getTotalBalanceFromDb();
        assertThat(totalFinalBalance)
                .withFailMessage("Общая сумма денег в системе изменилась! Было: %s, стало: %s",
                        totalInitialBalance, totalFinalBalance)
                .isEqualByComparingTo(totalInitialBalance);

        for (UUID senderId : senderIds) {
            assertThat(getBalanceFromDb(senderId))
                    .withFailMessage("У отправителя %s остался неверный баланс", senderId)
                    .isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    private List<UUID> createAccountsInDb(int count, BigDecimal initialBalance) {
        return IntStream.range(0, count)
                .mapToObj(i -> {
                    UUID accountId = UUID.randomUUID();
                    jdbcTemplate.update(
                            "INSERT INTO \"accounts\" (\"id\", \"balance\", \"status\") VALUES (?, ?, 'ACTIVE')",
                            accountId, initialBalance);
                    return accountId;
                })
                .collect(Collectors.toList());
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
