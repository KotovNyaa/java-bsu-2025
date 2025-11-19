package com.bank.application;

import com.bank.application.service.TransactionService;
import com.bank.core.command.ActionType;
import com.bank.core.command.TransactionCommand;
import com.bank.core.state.AccountState;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CrashRecoveryTest {

    private EmbeddedDatabase dataSource;
    private JdbcTemplate jdbcTemplate;
    private BankApplication bankApplication;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeAll
    void setupDatabase() {
        this.dataSource = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .setName("testdb_crash;MODE=PostgreSQL;DATABASE_TO_UPPER=false")
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
    }

    @AfterEach
    void tearDown() {
        if (this.bankApplication != null) {
            this.bankApplication.stop();
        }
    }

    @Test
    void should_recoverStuckProcessingTransactions_onStartup() throws Exception {

        UUID accountId = UUID.randomUUID();
        createAccountInDb(accountId, new BigDecimal("1000.00"));

        UUID idempotencyKey = UUID.randomUUID();
        TransactionCommand stuckCommand = TransactionCommand.createWithdrawCommand(
                idempotencyKey, accountId, new BigDecimal("100.00"));

        insertStuckTransaction(stuckCommand);

        assertThat(getRowCount("transaction_outbox")).isOne();
        assertThat(getStatusInDb(idempotencyKey)).isEqualTo("PROCESSING");
        assertThat(getBalanceFromDb(accountId)).isEqualByComparingTo("1000.00");

        this.bankApplication = new BankApplication(this.dataSource);
        this.bankApplication.start();

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertThat(getRowCount("transaction_outbox")).isZero());

        assertThat(getBalanceFromDb(accountId)).isEqualByComparingTo("900.00");

        assertThat(getRowCount("transaction_journal")).isOne();
    }

    @Test
    void should_NOT_reExecuteTransaction_ifAlreadyProcessed_onStartup() throws Exception {

        UUID accountId = UUID.randomUUID();
        createAccountInDb(accountId, new BigDecimal("900.00"));

        UUID idempotencyKey = UUID.randomUUID();
        TransactionCommand command = TransactionCommand.createWithdrawCommand(
                idempotencyKey, accountId, new BigDecimal("100.00"));

        insertStuckTransaction(command);

        jdbcTemplate.update(
                "INSERT INTO \"processed_transactions\" (\"idempotency_key\", \"processed_at\") VALUES (?, ?)",
                idempotencyKey, Timestamp.from(Instant.now()));

        this.bankApplication = new BankApplication(this.dataSource);
        this.bankApplication.start();

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertThat(getRowCount("transaction_outbox")).isZero());

        assertThat(getBalanceFromDb(accountId)).isEqualByComparingTo("900.00");
    }

    private void insertStuckTransaction(TransactionCommand cmd) throws Exception {
        String sql = "INSERT INTO \"transaction_outbox\" " +
                "(\"idempotency_key\", \"transaction_id\", \"payload\", \"status\", \"created_at\") " +
                "VALUES (?, ?, ?, 'PROCESSING', ?)";

        jdbcTemplate.update(sql,
                cmd.getIdempotencyKey(),
                cmd.getTransactionId(),
                objectMapper.writeValueAsString(cmd),
                Timestamp.from(Instant.now()));
    }

    private void createAccountInDb(UUID id, BigDecimal balance) {
        jdbcTemplate.update("INSERT INTO \"accounts\" (\"id\", \"balance\", \"status\") VALUES (?, ?, 'ACTIVE')", id,
                balance);
    }

    private BigDecimal getBalanceFromDb(UUID accountId) {
        return jdbcTemplate.queryForObject("SELECT \"balance\" FROM \"accounts\" WHERE \"id\" = ?", BigDecimal.class,
                accountId);
    }

    private String getStatusInDb(UUID key) {
        return jdbcTemplate.queryForObject(
                "SELECT \"status\" FROM \"transaction_outbox\" WHERE \"idempotency_key\" = ?", String.class, key);
    }

    private int getRowCount(String tableName) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM \"" + tableName + "\"", Integer.class);
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
