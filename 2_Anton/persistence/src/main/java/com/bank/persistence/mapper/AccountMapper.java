package com.bank.persistence.mapper;

import com.bank.domain.Account;
import com.bank.domain.AccountStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Ставит в соответствии строке из ResultSet аккаунт
 */

public class AccountMapper {

    public Account mapRow(ResultSet rs) throws SQLException {
        UUID id = rs.getObject("id", UUID.class);
        BigDecimal balance = rs.getBigDecimal("balance");
        String statusStr = rs.getString("status");

        AccountStatus status = AccountStatus.ACTIVE;
        if (statusStr != null) {
            try {
                status = AccountStatus.valueOf(statusStr.toUpperCase());
            } catch (IllegalArgumentException e) {
            }
        }

        return new Account(id, balance, status);
    }
}
