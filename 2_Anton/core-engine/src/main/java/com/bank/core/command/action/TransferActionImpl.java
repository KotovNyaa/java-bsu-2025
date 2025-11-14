package com.bank.core.command.action;

import com.bank.core.command.TransactionCommand;
import com.bank.domain.Account;
import com.bank.core.exception.InsufficientFundsException;

import java.math.BigDecimal;

/**
 * Атомарная операция перевода между счетами
 */

public class TransferActionImpl implements TransferAction {
    @Override
    public void execute(Account sourceAccount, Account targetAccount, TransactionCommand command) throws InsufficientFundsException {
        BigDecimal amount = command.getAmount();

        if (sourceAccount.getBalance().compareTo(amount) < 0) {
            throw new InsufficientFundsException("Insufficient funds on source account: " + sourceAccount.getId());
        }

        sourceAccount.withdraw(amount);
        targetAccount.deposit(amount);
    }
}
