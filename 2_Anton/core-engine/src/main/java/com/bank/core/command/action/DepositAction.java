package com.bank.core.command.action;

import com.bank.core.command.TransactionCommand;
import com.bank.core.domain.Account;

/**
 * Для пополнения счета
 */

public class DepositAction implements SingleAccountAction {
    @Override
    public void execute(Account account, TransactionCommand command) {
        account.deposit(command.getAmount());
    }
}
