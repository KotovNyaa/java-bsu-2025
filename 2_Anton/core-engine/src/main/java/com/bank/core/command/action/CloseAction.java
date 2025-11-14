package com.bank.core.command.action;

import com.bank.core.command.TransactionCommand;
import com.bank.domain.Account;

/**
 * Интерфейс для закрытия счета
 */

public class CloseAction implements SingleAccountAction {
    @Override
    public void execute(Account account, TransactionCommand command) {
        account.close();
    }
}
