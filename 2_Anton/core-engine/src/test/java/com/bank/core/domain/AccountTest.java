package com.bank.core.domain;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class AccountTest {

    @Test
    void deposit_shouldIncreaseBalance() {
        Account account = new Account(UUID.randomUUID(), new BigDecimal("100.00"));
        account.deposit(new BigDecimal("50.50"));
        assertEquals(0, new BigDecimal("150.50").compareTo(account.getBalance()));
    }

    @Test
    void withdraw_shouldDecreaseBalance() {
        Account account = new Account(UUID.randomUUID(), new BigDecimal("100.00"));
        account.withdraw(new BigDecimal("25.00"));
        assertEquals(0, new BigDecimal("75.00").compareTo(account.getBalance()));
    }

    @Test
    void freeze_shouldChangeStatusToFrozen() {
        Account account = new Account(UUID.randomUUID(), BigDecimal.TEN);
        account.freeze();
        assertEquals(AccountStatus.FROZEN, account.getStatus());
    }

    @Test
    void activate_shouldChangeStatusToActive() {
        Account account = new Account(UUID.randomUUID(), BigDecimal.TEN);
        account.freeze();
        account.activate();
        assertEquals(AccountStatus.ACTIVE, account.getStatus());
    }
}
