package com.bank.domain;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * POJO состояния счета
 */

public class Account {
    private final UUID id;
    private BigDecimal balance;
    private AccountStatus status;

    public Account(UUID id, BigDecimal initialBalance) {
        this.id = id;
        this.balance = initialBalance;
        this.status = AccountStatus.ACTIVE;
    }

    public void deposit(BigDecimal amount) {
        this.balance = this.balance.add(amount);
    }

    public void withdraw(BigDecimal amount) {
        this.balance = this.balance.subtract(amount);
    }

    public void freeze() {
        this.status = AccountStatus.FROZEN;
    }

    public void activate() {
        this.status = AccountStatus.ACTIVE;
    }
    
    public UUID getId() {
        return id;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public AccountStatus getStatus() {
        return status;
    }
}
