package com.bank.persistence.mapper;

import com.bank.domain.Account;
import com.bank.domain.AccountStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/**
 * Ставит в соответствии строке из ResultSet аккаунт
 */

public final class AccountMapper {

    private AccountMapper() {
    }

    public static Account mapRow(ResultSet rs) throws SQLException {
        UUID id = rs.getObject("id", UUID.class);
        return new Account(id, rs.getBigDecimal("balance"));
    }
}
