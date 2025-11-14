package com.bank.persistence.integration;

import com.bank.core.command.ActionType;
import com.bank.core.command.TransactionCommand;
import com.bank.domain.Account;
import com.bank.persistence.repository.impl.JdbcAccountRepository;
import com.bank.persistence.repository.impl.JdbcJournalRepository;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.FileReader;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CorePersistenceIntegrationTest {

    private JdbcDataSource dataSource;
    private JdbcAccountRepository accountRepository;
    private JdbcJournalRepository journalRepository;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:integrationdb;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("sa");

        try (Connection conn = dataSource.getConnection()) {
            java.net.URL resource = getClass().getClassLoader().getResource("schema.sql");
            if (resource == null)
                throw new IllegalStateException("Cannot find schema.sql");
            org.h2.tools.RunScript.execute(conn, new FileReader(resource.getFile()));
        }

        accountRepository = new JdbcAccountRepository(dataSource);
        journalRepository = new JdbcJournalRepository(dataSource);
    }

    @Test
    void should_correctly_restore_state_from_journal_after_simulated_crash() throws Exception {
        UUID accountId1 = UUID.randomUUID();
        UUID accountId2 = UUID.randomUUID();
        Map<UUID, Account> initialState = new HashMap<>();
        initialState.put(accountId1, new Account(accountId1, new BigDecimal("1000")));
        initialState.put(accountId2, new Account(accountId2, new BigDecimal("500")));

        try (Connection conn = dataSource.getConnection()) {
            String sql = "INSERT INTO accounts (id, balance, status) VALUES (?, ?, 'ACTIVE')";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, accountId1);
                stmt.setBigDecimal(2, new BigDecimal("1000"));
                stmt.executeUpdate();

                stmt.setObject(1, accountId2);
                stmt.setBigDecimal(2, new BigDecimal("500"));
                stmt.executeUpdate();
            }
        }

        TransactionCommand deposit = TransactionCommand.createDepositCommand(accountId1, new BigDecimal("200"));
        TransactionCommand withdraw = TransactionCommand.createWithdrawCommand(accountId2, new BigDecimal("100"));
        TransactionCommand transfer = TransactionCommand.createTransferCommand(accountId1, accountId2,
                new BigDecimal("300"));

        journalRepository.log(deposit);
        journalRepository.log(withdraw);
        journalRepository.log(transfer);

        Map<UUID, Account> recoveredState = accountRepository.loadAllAccounts();
        assertNotNull(recoveredState);
        assertEquals(2, recoveredState.size());
        assertEquals(0, new BigDecimal("1000").compareTo(recoveredState.get(accountId1).getBalance()));

        try (Connection conn = dataSource.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT * FROM transaction_journal ORDER BY sequence_id ASC")) {

            while (rs.next()) {
                ActionType type = ActionType.valueOf(rs.getString("command_type"));
                UUID fromId = rs.getObject("account_id_from", UUID.class);
                UUID toId = rs.getObject("account_id_to", UUID.class);
                BigDecimal amount = rs.getBigDecimal("amount");

                Account sourceAccount = recoveredState.get(fromId);

                switch (type) {
                    case DEPOSIT:
                        sourceAccount.deposit(amount);
                        break;
                    case WITHDRAW:
                        sourceAccount.withdraw(amount);
                        break;
                    case TRANSFER:
                        Account targetAccount = recoveredState.get(toId);
                        sourceAccount.withdraw(amount);
                        targetAccount.deposit(amount);
                        break;
                }
            }
        }

        assertEquals(0, new BigDecimal("900").compareTo(recoveredState.get(accountId1).getBalance()),
                "Balance of account 1 is incorrect after recovery");
        assertEquals(0, new BigDecimal("700").compareTo(recoveredState.get(accountId2).getBalance()),
                "Balance of account 2 is incorrect after recovery");
    }
}
