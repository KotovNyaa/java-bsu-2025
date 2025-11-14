package com.bank.persistence.repository.impl;

import com.bank.domain.Account;
import com.bank.persistence.exception.DataAccessException;
import com.bank.persistence.mapper.AccountMapper;
import com.bank.persistence.repository.AccountRepository;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;

/**
 * Интерфейс AccountRepository на базе JDBC
 */

public final class JdbcAccountRepository implements AccountRepository {

    private final DataSource dataSource;

    public JdbcAccountRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Map<UUID, Account> loadAllAccounts() {
        final String sql = "SELECT id, balance, status FROM accounts";
        Map<UUID, Account> accounts = new HashMap<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                Account account = AccountMapper.mapRow(rs);
                accounts.put(account.getId(), account);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load all accounts from the database", e);
        }
        return accounts;
    }
}
