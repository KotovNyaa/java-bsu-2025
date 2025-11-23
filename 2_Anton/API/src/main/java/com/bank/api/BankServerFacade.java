package com.bank.api;

import com.bank.api.dto.CommandResponse;
import com.bank.api.dto.SystemStateDto;
import com.bank.api.service.AdminService;
import com.bank.api.service.ApiTransactionService;
import com.bank.api.service.QueryService;
import com.bank.application.BankApplication;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class BankServerFacade {

    private final BankApplication bankApplication;
    private final AdminService adminService;
    private final ApiTransactionService transactionService;
    private final QueryService queryService;
    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    private final ScheduledExecutorService observerExecutor = Executors.newSingleThreadScheduledExecutor();
    private final List<Consumer<UUID>> observers = new CopyOnWriteArrayList<>();
    private Timestamp lastCheckTime = Timestamp.from(Instant.now());

    public BankServerFacade() {
        this(false);
    }

    public BankServerFacade(boolean testMode) {
        if (testMode) {
            this.dataSource = new EmbeddedDatabaseBuilder()
                    .setType(EmbeddedDatabaseType.H2)
                    .setName("test_db;MODE=PostgreSQL;DATABASE_TO_UPPER=false")
                    .addScript("classpath:schema.sql") 
                    .build();
            this.jdbcTemplate = new JdbcTemplate(this.dataSource);
            initUiTables(this.jdbcTemplate);
        } else {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl("jdbc:h2:./bank_storage;MODE=PostgreSQL;DATABASE_TO_UPPER=false;DB_CLOSE_ON_EXIT=FALSE;TRACE_LEVEL_FILE=0;LOCK_TIMEOUT=10000");
            config.setDriverClassName("org.h2.Driver");
            config.setUsername("sa");
            config.setPassword("");
            config.setMaximumPoolSize(10); 
            config.setMinimumIdle(2);
            config.setIdleTimeout(60000);
            config.setPoolName("BankHikariPool");
            config.setValidationTimeout(5000);
            config.setConnectionTestQuery("SELECT 1");

            this.dataSource = new HikariDataSource(config);
            this.jdbcTemplate = new JdbcTemplate(this.dataSource);
            
            initAllTablesSafe(this.jdbcTemplate);
        }

        this.adminService = new AdminService(this.jdbcTemplate);
        this.transactionService = new ApiTransactionService(this.jdbcTemplate);
        this.queryService = new QueryService(this.jdbcTemplate);

        this.bankApplication = new BankApplication(this.dataSource);
        this.bankApplication.start();

        observerExecutor.scheduleWithFixedDelay(this::checkProcessedTransactions, 1000, 100, TimeUnit.MILLISECONDS);
    }

    private void checkProcessedTransactions() {
        if (observers.isEmpty()) return;

        try {
            String sql = "SELECT \"idempotency_key\", \"processed_at\" FROM \"processed_transactions\" WHERE \"processed_at\" > ?";
            
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, lastCheckTime);
            
            if (!rows.isEmpty()) {
                for (Map<String, Object> row : rows) {
                    Timestamp processedAt = (Timestamp) row.get("processed_at");
                    if (processedAt.after(lastCheckTime)) {
                        lastCheckTime = processedAt;
                    }
                    UUID key = (UUID) row.get("idempotency_key");
                    for (Consumer<UUID> obs : observers) {
                        obs.accept(key);
                    }
                }
            }
        } catch (Exception e) {
            // Silent catch
        }
    }

    public void addObserver(Consumer<UUID> observer) {
        this.observers.add(observer);
    }

    private void initUiTables(JdbcTemplate jdbc) {
        jdbc.execute("CREATE TABLE IF NOT EXISTS \"users\" (\"id\" UUID PRIMARY KEY, \"nickname\" VARCHAR(255) NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS \"user_accounts\" (\"user_id\" UUID NOT NULL, \"account_id\" UUID NOT NULL, PRIMARY KEY (\"user_id\", \"account_id\"), FOREIGN KEY (\"user_id\") REFERENCES \"users\"(\"id\"), FOREIGN KEY (\"account_id\") REFERENCES \"accounts\"(\"id\"))");
    }

    private void initAllTablesSafe(JdbcTemplate jdbc) {
        jdbc.execute("CREATE TABLE IF NOT EXISTS \"accounts\" (\"id\" UUID PRIMARY KEY, \"balance\" DECIMAL(19, 2) NOT NULL, \"status\" VARCHAR(20) NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS \"idempotency_keys\" (\"key\" UUID PRIMARY KEY, \"created_at\" TIMESTAMP NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS \"transaction_outbox\" (\"idempotency_key\" UUID PRIMARY KEY, \"transaction_id\" UUID NOT NULL UNIQUE, \"payload\" VARCHAR(2048) NOT NULL, \"status\" VARCHAR(20) DEFAULT 'PENDING' NOT NULL, \"attempts\" INT DEFAULT 0 NOT NULL, \"failure_count\" INT DEFAULT 0 NOT NULL, \"created_at\" TIMESTAMP NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS \"transaction_outbox_dlq\" (\"id\" UUID PRIMARY KEY, \"payload\" VARCHAR(2048) NOT NULL, \"reason\" VARCHAR(1024), \"moved_at\" TIMESTAMP NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS \"processed_transactions\" (\"idempotency_key\" UUID PRIMARY KEY, \"processed_at\" TIMESTAMP NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS \"transaction_journal\" (\"sequence_id\" BIGSERIAL PRIMARY KEY, \"idempotency_key\" UUID NOT NULL UNIQUE, \"transaction_id\" UUID NOT NULL, \"timestamp\" TIMESTAMP NOT NULL, \"command_type\" VARCHAR(50) NOT NULL, \"account_id_from\" UUID NOT NULL, \"account_id_to\" UUID, \"amount\" DECIMAL(19, 2))");

        jdbc.execute("CREATE TABLE IF NOT EXISTS \"users\" (\"id\" UUID PRIMARY KEY, \"nickname\" VARCHAR(255) NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS \"user_accounts\" (\"user_id\" UUID NOT NULL, \"account_id\" UUID NOT NULL, PRIMARY KEY (\"user_id\", \"account_id\"), FOREIGN KEY (\"user_id\") REFERENCES \"users\"(\"id\"), FOREIGN KEY (\"account_id\") REFERENCES \"accounts\"(\"id\"))");
    }

    public void stop() {
        observerExecutor.shutdownNow();
        this.bankApplication.stop();
        if (this.dataSource instanceof HikariDataSource) {
            ((HikariDataSource) this.dataSource).close();
        }
    }

    public void killApp() {
        Runtime.getRuntime().halt(9); 
    }

    public CommandResponse reset() {
        this.lastCheckTime = Timestamp.from(Instant.now());
        return adminService.resetSystem();
    }

    public CommandResponse createUser(String nickname) { return adminService.createUser(nickname); }
    public CommandResponse createAccount(UUID userId) { return adminService.createAccount(userId); }
    
    public CommandResponse deposit(UUID accountId, BigDecimal amount) { return transactionService.deposit(accountId, amount); }
    public CommandResponse withdraw(UUID accountId, BigDecimal amount) { return transactionService.withdraw(accountId, amount); }
    public CommandResponse transfer(UUID from, UUID to, BigDecimal amount) { return transactionService.transfer(from, to, amount); }
    public CommandResponse freeze(UUID accountId) { return transactionService.freeze(accountId); }
    public CommandResponse unfreeze(UUID accountId) { return transactionService.unfreeze(accountId); }
    public CommandResponse close(UUID accountId) { return transactionService.close(accountId); }
    
    public SystemStateDto getSystemState() { return queryService.getSystemState(); }
}
