package com.bank.core.command.action;

import com.bank.core.command.TransactionCommand;
import com.bank.domain.Account;
import com.bank.domain.AccountStatus;
import com.bank.core.exception.InsufficientFundsException;

import java.math.BigDecimal;

/**
 * Атомарная операция перевода между счетами
 */

public class TransferActionImpl implements TransferAction {
    public void execute(Account sourceAccount, Account targetAccount, TransactionCommand command)
    throws InsufficientFundsException {

    if (sourceAccount.getId().equals(targetAccount.getId())) {
        throw new IllegalArgumentException("Счет-отправитель и счет-получатель не могут совпадать");
    }

    if (command.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
        throw new IllegalArgumentException("Сумма для перевода должна быть положительной");
    }

    if (sourceAccount.getStatus() != AccountStatus.ACTIVE) {
        throw new IllegalStateException("Счет-отправитель неактивен: " + sourceAccount.getStatus());
    }
    if (targetAccount.getStatus() != AccountStatus.ACTIVE) {
        throw new IllegalStateException("Счет-получатель неактивен: " + targetAccount.getStatus());
    }

    if (sourceAccount.getBalance().compareTo(command.getAmount()) < 0) {
        throw new InsufficientFundsException("Недостаточно средств на счете " + sourceAccount.getId());
    }

    sourceAccount.withdraw(command.getAmount());
    targetAccount.deposit(command.getAmount());
    }
}
