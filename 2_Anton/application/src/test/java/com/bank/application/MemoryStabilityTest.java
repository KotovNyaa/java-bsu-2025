package com.bank.application;

import com.bank.application.service.TransactionService;
import com.bank.core.state.AccountState;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MemoryStabilityTest {

    private static final Logger log = LoggerFactory.getLogger(MemoryStabilityTest.class);

    private static final int ACCOUNT_COUNT = 100;
    private static final int TOTAL_CYCLES = 1;
    private static final int TX_PER_CYCLE = 20_000;

    private static final long MAX_ALLOWED_GROWTH_MB = 10;

    private EmbeddedDatabase dataSource;
    private JdbcTemplate jdbcTemplate;
    private BankApplication bankApplication;
    private TransactionService transactionService;
    private List<UUID> accountIds;

    @BeforeAll
    void setupDatabase() {
        this.dataSource = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .setName("testdb_stress;MODE=PostgreSQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1")
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

        this.accountIds = createAccounts(ACCOUNT_COUNT);
    }

    @AfterEach
    void tearDown() {
        if (this.bankApplication != null) {
            this.bankApplication.stop();
        }
    }

    @Test
    void memoryShouldNotLeak_overLongRun() throws InterruptedException {
        log.info("Warming up...");
        runBatch(1000);
        waitAndCleanHistory();
        bankApplication.clearIdempotencyCache();
        forceGc();
        long baselineMemory = getUsedMemoryMb();
        log.info("Baseline Memory: {} MB", baselineMemory);

        for (int cycle = 1; cycle <= TOTAL_CYCLES; cycle++) {
            log.info("Cycle {}/{}: Running {} transactions...", cycle, TOTAL_CYCLES, TX_PER_CYCLE);

            runBatch(TX_PER_CYCLE);

            waitAndCleanHistory();

            bankApplication.clearIdempotencyCache();

            forceGc();

            long currentMemory = getUsedMemoryMb();
            long diff = currentMemory - baselineMemory;
            log.info("Cycle {} finished. Current Memory: {} MB (Diff: {} MB)", cycle, currentMemory, diff);

            assertThat(diff)
                    .withFailMessage("Memory leak detected! Memory grew by %d MB after cleanup.", diff)
                    .isLessThanOrEqualTo(MAX_ALLOWED_GROWTH_MB);
        }
    }

    private void runBatch(int count) {
        for (int i = 0; i < count; i++) {
            UUID from = accountIds.get(i % ACCOUNT_COUNT);
            UUID to = accountIds.get((i + 1) % ACCOUNT_COUNT);
            transactionService.transfer(UUID.randomUUID(), from, to, BigDecimal.ONE);
        }
    }

    private void waitAndCleanHistory() {
        await().atMost(30, TimeUnit.SECONDS).pollInterval(200, TimeUnit.MILLISECONDS)
                .until(() -> getRowCount("transaction_outbox") == 0);

        jdbcTemplate.execute("TRUNCATE TABLE \"transaction_journal\"");
        jdbcTemplate.execute("TRUNCATE TABLE \"processed_transactions\"");
        jdbcTemplate.execute("TRUNCATE TABLE \"idempotency_keys\"");
        jdbcTemplate.execute("TRUNCATE TABLE \"transaction_outbox_dlq\"");
    }

    private List<UUID> createAccounts(int count) {
        List<UUID> ids = new ArrayList<>();
        String sql = "INSERT INTO \"accounts\" (\"id\", \"balance\", \"status\") VALUES (?, ?, 'ACTIVE')";
        for (int i = 0; i < count; i++) {
            UUID id = UUID.randomUUID();
            jdbcTemplate.update(sql, id, new BigDecimal("1000000.00"));

            com.bank.domain.Account acc = new com.bank.domain.Account(id, new BigDecimal("1000000.00"),
                    com.bank.domain.AccountStatus.ACTIVE);
            AccountState.INSTANCE.createOrUpdateAccount(acc);

            ids.add(id);
        }
        return ids;
    }

    private void forceGc() {
        System.gc();
        System.runFinalization();
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
        }
    }

    private long getUsedMemoryMb() {
        Runtime runtime = Runtime.getRuntime();
        return (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
    }

    private void cleanAllTables() {
        jdbcTemplate.execute("TRUNCATE TABLE \"processed_transactions\" RESTART IDENTITY");
        jdbcTemplate.execute("TRUNCATE TABLE \"transaction_journal\" RESTART IDENTITY");
        jdbcTemplate.execute("TRUNCATE TABLE \"transaction_outbox_dlq\" RESTART IDENTITY");
        jdbcTemplate.execute("TRUNCATE TABLE \"transaction_outbox\" RESTART IDENTITY");
        jdbcTemplate.execute("TRUNCATE TABLE \"idempotency_keys\" RESTART IDENTITY");
        jdbcTemplate.execute("TRUNCATE TABLE \"accounts\" RESTART IDENTITY");
    }

    private int getRowCount(String tableName) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM \"" + tableName + "\"", Integer.class);
    }
}
