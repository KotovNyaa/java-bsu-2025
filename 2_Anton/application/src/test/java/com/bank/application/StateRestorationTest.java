package com.bank.application;

import com.bank.application.service.TransactionService;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StateRestorationTest {

    private EmbeddedDatabase dataSource;
    private BankApplication bankApplication;
    private TransactionService transactionService;
    private JdbcTemplate jdbcTemplate;

    @BeforeAll
    void setupDatabase() {
        this.dataSource = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .addScript("classpath:schema.sql")
                .build();
        this.jdbcTemplate = new JdbcTemplate(this.dataSource);
    }

    @AfterAll
    void shutdownDatabase() {
        this.dataSource.shutdown();
    }

    @AfterEach
    void tearDownApplication() {
        if (this.bankApplication != null) {
            this.bankApplication.stop();
        }
    }

    private void startApplication(DataSource ds) {
        this.bankApplication = new BankApplication(ds);
        this.bankApplication.start();
        this.transactionService = this.bankApplication.getTransactionService();
    }

    @Test
    void should_loadExistingAccountsFromDatabase_onApplicationStart() {
        UUID accountId = UUID.fromString("c7a365a3-7b71-4f13-8b74-2900c926414f");
        BigDecimal initialBalance = new BigDecimal("1000.50");
        String status = "ACTIVE";

        jdbcTemplate.update("DELETE FROM \"accounts\"");

        jdbcTemplate.update(
                "INSERT INTO \"accounts\" (\"id\", \"balance\", \"status\") VALUES (?, ?, ?)",
                accountId, initialBalance, status);

        startApplication(this.dataSource);

        try {
            BigDecimal balanceFromService = transactionService.getBalance(accountId);
            assertThat(balanceFromService)
                    .withFailMessage("Баланс, полученный из сервиса, не совпадает с тем, что в БД.")
                    .isEqualByComparingTo(initialBalance);

        } catch (Exception e) {
            fail(String.format("Приложение не смогло найти счет с ID=%s, который точно существует в БД. " +
                    "Вероятная причина: механизм восстановления состояния не реализован. Ошибка: %s",
                    accountId, e.getClass().getSimpleName()));
        }
    }
}
