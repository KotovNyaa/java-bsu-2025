package com.bank.core.command.action;

import com.bank.core.command.TransactionCommand;
import com.bank.domain.Account;
import com.bank.core.exception.InsufficientFundsException;

import java.math.BigDecimal;

/**
 * Для снятия со счета
 */

public class WithdrawAction implements SingleAccountAction {
    @Override
    public void execute(Account account, TransactionCommand command) throws InsufficientFundsException {
        BigDecimal amount = command.getAmount();

        if (account.getBalance().compareTo(amount) < 0) {
            throw new InsufficientFundsException("Insufficient funds on account: " + account.getId());
        }

        account.withdraw(amount);
    }
}
