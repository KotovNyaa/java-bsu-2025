package com.bank.persistence.repository.impl;

import com.bank.domain.Account;
import com.bank.domain.AccountStatus;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcAccountRepositoryTest {

    private EmbeddedDatabase dataSource;
    private JdbcTemplate jdbcTemplate;
    private JdbcAccountRepository repository;

    @BeforeAll
    void setupDatabase() {
        this.dataSource = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .setName("testdb_repo;MODE=PostgreSQL;DATABASE_TO_UPPER=false")
                .addScript("classpath:schema.sql")
                .build();
        this.jdbcTemplate = new JdbcTemplate(this.dataSource);
        this.repository = new JdbcAccountRepository(this.dataSource);
    }

    @BeforeEach
    void cleanTablesBeforeTest() {
        jdbcTemplate.update("DELETE FROM \"accounts\"");
    }

    @AfterAll
    void shutdownDatabase() {
        this.dataSource.shutdown();
    }

    @Test
    void loadAllAccounts_should_return_all_accounts() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();

        String sql = "INSERT INTO \"accounts\" (\"id\", \"balance\", \"status\") VALUES (?, ?, ?)";
        jdbcTemplate.update(sql, id1, new BigDecimal("100.50"), "ACTIVE");
        jdbcTemplate.update(sql, id2, new BigDecimal("250.00"), "FROZEN");

        Map<UUID, Account> accounts = repository.loadAllAccounts();

        assertEquals(2, accounts.size());
        assertTrue(accounts.containsKey(id1));
        assertTrue(accounts.containsKey(id2));

        Account acc1 = accounts.get(id1);
        assertEquals(0, new BigDecimal("100.50").compareTo(acc1.getBalance()));
        assertEquals(AccountStatus.ACTIVE, acc1.getStatus());

        Account acc2 = accounts.get(id2);
        assertEquals(0, new BigDecimal("250.00").compareTo(acc2.getBalance()));
        assertEquals(AccountStatus.FROZEN, acc2.getStatus());
    }

    @Test
    void loadAllAccounts_should_return_empty_map_when_table_is_empty() {
        Map<UUID, Account> accounts = repository.loadAllAccounts();
        assertNotNull(accounts);
        assertTrue(accounts.isEmpty());
    }

    @Test
    void should_load_account_with_correct_frozen_status() {
        UUID frozenAccountId = UUID.randomUUID();
        BigDecimal balance = new BigDecimal("1000.00");
        String sql = "INSERT INTO \"accounts\" (\"id\", \"balance\", \"status\") VALUES (?, ?, ?)";

        jdbcTemplate.update(sql, frozenAccountId, balance, "FROZEN");

        Map<UUID, Account> accounts = repository.loadAllAccounts();

        assertNotNull(accounts.get(frozenAccountId), "Счет не был загружен");
        assertEquals(
                AccountStatus.FROZEN,
                accounts.get(frozenAccountId).getStatus(),
                "Статус счета должен быть FROZEN");
        assertEquals(0, balance.compareTo(accounts.get(frozenAccountId).getBalance()));
    }
}
