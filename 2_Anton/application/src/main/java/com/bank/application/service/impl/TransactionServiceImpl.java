package com.bank.application.service.impl;

import com.bank.application.port.out.TransactionalOutboxRepository;
import com.bank.application.port.out.TransactionStatusProvider;
import com.bank.application.service.TransactionService;
import com.bank.application.service.TransactionStatus;
import com.bank.application.visitor.ReportVisitor;
import com.bank.core.command.TransactionCommand;
import com.bank.core.exception.AccountNotFoundException;
import com.bank.core.state.AccountStateProvider;
import com.bank.domain.Account;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * TransactionService использующий только Transactional Outbox
 */

public final class TransactionServiceImpl implements TransactionService {

    private final AccountStateProvider stateProvider;
    private final TransactionStatusProvider statusProvider;
    private final TransactionalOutboxRepository outboxRepository;

    public TransactionServiceImpl(
            AccountStateProvider stateProvider,
            TransactionStatusProvider statusProvider,
            TransactionalOutboxRepository outboxRepository) {
        this.stateProvider = stateProvider;
        this.statusProvider = statusProvider;
        this.outboxRepository = outboxRepository;
    }

    @Override
    public void deposit(UUID idempotencyKey, UUID accountId, BigDecimal amount) {
        validateAll(idempotencyKey, accountId, amount);
        TransactionCommand command = TransactionCommand.createDepositCommand(idempotencyKey, accountId, amount);
        outboxRepository.save(command);
    }

    @Override
    public void withdraw(UUID idempotencyKey, UUID accountId, BigDecimal amount) {
        validateAll(idempotencyKey, accountId, amount);
        TransactionCommand command = TransactionCommand.createWithdrawCommand(idempotencyKey, accountId, amount);
        outboxRepository.save(command);
    }

    @Override
    public void transfer(UUID idempotencyKey, UUID fromAccountId, UUID toAccountId, BigDecimal amount) {
        validateAll(idempotencyKey, fromAccountId, amount);
        validateAccountId(toAccountId, "ToAccountId");
        if (Objects.equals(fromAccountId, toAccountId)) {
            throw new IllegalArgumentException("Sender and receiver accounts cannot be the same.");
        }
        TransactionCommand command = TransactionCommand.createTransferCommand(idempotencyKey, fromAccountId, toAccountId, amount);
        outboxRepository.save(command);
    }

    @Override
    public void freezeAccount(UUID idempotencyKey, UUID accountId) {
        validateAll(idempotencyKey, accountId);
        outboxRepository.save(TransactionCommand.createFreezeCommand(idempotencyKey, accountId));
    }

    @Override
    public void unfreezeAccount(UUID idempotencyKey, UUID accountId) {
        validateAll(idempotencyKey, accountId);
        outboxRepository.save(TransactionCommand.createUnfreezeCommand(idempotencyKey, accountId));
    }

    @Override
    public void closeAccount(UUID idempotencyKey, UUID accountId) {
        validateAll(idempotencyKey, accountId);
        outboxRepository.save(TransactionCommand.createCloseCommand(idempotencyKey, accountId));
    }

    @Override
    public TransactionStatus getTransactionStatus(UUID idempotencyKey) {
        validateIdempotencyKey(idempotencyKey);
        return statusProvider.findStatusByIdempotencyKey(idempotencyKey);
    }

    @Override
    public BigDecimal getBalance(UUID accountId) throws AccountNotFoundException {
        validateAccountId(accountId);
        return stateProvider.getAccount(accountId).getBalance();
    }

    @Override
    public String generateReport(UUID accountId, ReportVisitor visitor) throws AccountNotFoundException {
        validateAccountId(accountId);
        Objects.requireNonNull(visitor, "Visitor cannot be null");
        Account account = stateProvider.getAccount(accountId);
        return visitor.visit(account);
    }

    private void validateAll(UUID key, UUID id, BigDecimal amount) {
        Objects.requireNonNull(key, "Idempotency key cannot be null");
        Objects.requireNonNull(id, "Account ID cannot be null");
        Objects.requireNonNull(amount, "Amount cannot be null");
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
    }
    private void validateAll(UUID key, UUID id) {
        Objects.requireNonNull(key, "Idempotency key cannot be null");
        Objects.requireNonNull(id, "Account ID cannot be null");
    }
    private void validateAccountId(UUID id) { Objects.requireNonNull(id, "Account ID cannot be null"); }
    private void validateAccountId(UUID id, String name) { Objects.requireNonNull(id, name + " cannot be null"); }
    private void validateIdempotencyKey(UUID key) { Objects.requireNonNull(key, "Idempotency key cannot be null"); }
}
