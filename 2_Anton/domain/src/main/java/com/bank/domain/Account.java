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

    public Account(UUID id, BigDecimal balance, AccountStatus status) {
        this.id = id;
        this.balance = balance;
        this.status = status;
    }

    public Account(UUID id, BigDecimal balance) {
        this(id, balance, AccountStatus.ACTIVE);
    }

    public Account(Account other) {
        this.id = other.id;
        this.balance = other.balance;
        this.status = other.status;
    }

    private void checkActive() {
        if (this.status != AccountStatus.ACTIVE) {
            throw new IllegalStateException("Операция не может быть выполнена, счет неактивен: " + this.status);
        }
    }

    public void deposit(BigDecimal amount) {
        checkActive();
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Сумма пополнения должна быть положительной");
        }
        this.balance = this.balance.add(amount);
    }

    public void withdraw(BigDecimal amount) {
        checkActive();
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Сумма снятия должна быть положительной");
        }
        this.balance = this.balance.subtract(amount);
    }

    public void freeze() {
        if (this.status == AccountStatus.CLOSED) {
            throw new IllegalStateException("Нельзя заморозить закрытый счет");
        }
        this.status = AccountStatus.FROZEN;
    }

    public void activate() {
        if (this.status == AccountStatus.CLOSED) {
            throw new IllegalStateException("Нельзя активировать закрытый счет");
        }
        this.status = AccountStatus.ACTIVE;
    }

    public void close() {
        this.status = AccountStatus.CLOSED;
    }

    public UUID getId() {
        return id;
    }

    public void setStatus(AccountStatus status) {
        this.status = status;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public AccountStatus getStatus() {
        return status;
    }
}
