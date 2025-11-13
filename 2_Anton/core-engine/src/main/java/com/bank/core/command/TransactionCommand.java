package com.bank.core.command;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Неизменяемый объект-команда, создаваемый через статические фабричные методы
 */

public final class TransactionCommand {

    private final UUID transactionId;
    private final UUID accountId;
    private final UUID targetAccountId;
    private final ActionType actionType;
    private final BigDecimal amount;

    private TransactionCommand(UUID transactionId, UUID accountId, ActionType actionType, BigDecimal amount, UUID targetAccountId) {
        this.transactionId = transactionId;
        this.accountId = accountId;
        this.actionType = actionType;
        this.amount = amount;
        this.targetAccountId = targetAccountId;
    }

    public static TransactionCommand createDepositCommand(UUID accountId, BigDecimal amount) {
        return new TransactionCommand(UUID.randomUUID(), accountId, ActionType.DEPOSIT, amount, null);
    }

    public static TransactionCommand createWithdrawCommand(UUID accountId, BigDecimal amount) {
        return new TransactionCommand(UUID.randomUUID(), accountId, ActionType.WITHDRAW, amount, null);
    }

    public static TransactionCommand createFreezeCommand(UUID accountId) {
        return new TransactionCommand(UUID.randomUUID(), accountId, ActionType.FREEZE, null, null);
    }

    public static TransactionCommand createTransferCommand(UUID fromAccountId, UUID toAccountId, BigDecimal amount) {
        return new TransactionCommand(UUID.randomUUID(), fromAccountId, ActionType.TRANSFER, amount, toAccountId);
    }

    public UUID getTransactionId() { return transactionId; }
    public UUID getAccountId() { return accountId; }
    public UUID getTargetAccountId() { return targetAccountId; }
    public ActionType getActionType() { return actionType; }
    public BigDecimal getAmount() { return amount; }
}
