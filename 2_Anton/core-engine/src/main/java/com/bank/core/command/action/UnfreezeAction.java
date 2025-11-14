package com.bank.core.command.action;

import com.bank.core.command.TransactionCommand;
import com.bank.domain.Account;

/**
 * Интерфейс для разморозки счета
 */

public class UnfreezeAction implements SingleAccountAction {
    @Override
    public void execute(Account account, TransactionCommand command) {
        account.activate();
    }
}
