package com.bank.persistence.repository.impl;

import com.bank.domain.Account;
import com.bank.domain.AccountStatus;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.FileReader;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JdbcAccountRepositoryTest {

    private JdbcDataSource dataSource;
    private JdbcAccountRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("sa");

        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            java.net.URL resource = getClass().getClassLoader().getResource("schema.sql");
            if (resource == null) throw new IllegalStateException("Cannot find schema.sql");
            org.h2.tools.RunScript.execute(conn, new FileReader(resource.getFile()));
        }

        repository = new JdbcAccountRepository(dataSource);
    }

    @Test
    @DisplayName("loadAllAccounts should return all accounts from the database")
    void loadAllAccounts_should_return_all_accounts() throws Exception {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();

        try (Connection conn = dataSource.getConnection()) {
            String sql = "INSERT INTO accounts (id, balance, status) VALUES (?, ?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, id1);
                stmt.setBigDecimal(2, new BigDecimal("100.50"));
                stmt.setString(3, "ACTIVE");
                stmt.addBatch();

                stmt.setObject(1, id2);
                stmt.setBigDecimal(2, new BigDecimal("250.00"));
                stmt.setString(3, "ACTIVE");
                stmt.addBatch();
                
                stmt.executeBatch();
            }
        }
        
        Map<UUID, Account> accounts = repository.loadAllAccounts();

        assertEquals(2, accounts.size());
        assertTrue(accounts.containsKey(id1));
        assertTrue(accounts.containsKey(id2));
        
        Account acc1 = accounts.get(id1);
        assertEquals(0, new BigDecimal("100.50").compareTo(acc1.getBalance()));
        assertEquals(AccountStatus.ACTIVE, acc1.getStatus());
        
        Account acc2 = accounts.get(id2);
        assertEquals(0, new BigDecimal("250.00").compareTo(acc2.getBalance()));
        assertEquals(AccountStatus.ACTIVE, acc2.getStatus());
    }

    @Test
    @DisplayName("loadAllAccounts should return an empty map if no accounts exist")
    void loadAllAccounts_should_return_empty_map() {
        Map<UUID, Account> accounts = repository.loadAllAccounts();
        assertNotNull(accounts);
        assertTrue(accounts.isEmpty());
    }
}
