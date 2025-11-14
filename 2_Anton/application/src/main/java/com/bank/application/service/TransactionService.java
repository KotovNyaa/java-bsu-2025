package com.bank.application.service;

import com.bank.application.visitor.ReportVisitor;
import com.bank.core.exception.AccountNotFoundException;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Публичный API. Методы-команды синхронны и не возвращают результат
 */

public interface TransactionService {

    void deposit(UUID idempotencyKey, UUID accountId, BigDecimal amount);

    void withdraw(UUID idempotencyKey, UUID accountId, BigDecimal amount);

    void transfer(UUID idempotencyKey, UUID fromAccountId, UUID toAccountId, BigDecimal amount);

    void freezeAccount(UUID idempotencyKey, UUID accountId);

    void unfreezeAccount(UUID idempotencyKey, UUID accountId);

    void closeAccount(UUID idempotencyKey, UUID accountId);

    BigDecimal getBalance(UUID accountId) throws AccountNotFoundException;

    String generateReport(UUID accountId, ReportVisitor visitor) throws AccountNotFoundException;

    TransactionStatus getTransactionStatus(UUID idempotencyKey);
}
