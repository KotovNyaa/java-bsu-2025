package com.bank.core.command.action;

import com.bank.core.command.TransactionCommand;
import com.bank.domain.Account;
import com.bank.domain.AccountStatus;
import java.math.BigDecimal;
import com.bank.core.exception.InsufficientFundsException;

import java.math.BigDecimal;

/**
 * Для снятия со счета
 */

public class WithdrawAction implements SingleAccountAction {
    @Override
    public void execute(Account account, TransactionCommand command) throws InsufficientFundsException {
        if (command.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Сумма для снятия должна быть положительной");
        }

        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new IllegalStateException("Операция не может быть выполнена, счет неактивен: " + account.getStatus());
        }

        if (account.getBalance().compareTo(command.getAmount()) < 0) {
            throw new InsufficientFundsException("Недостаточно средств на счете " + account.getId());
        }

        account.withdraw(command.getAmount());
    }
}
