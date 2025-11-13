package com.bank.core.command.action;

import com.bank.core.command.TransactionCommand;
import com.bank.core.domain.Account;

/**
 * Для заморозки счета
 */

public class FreezeAction implements SingleAccountAction {
    @Override
    public void execute(Account account, TransactionCommand command) {
        account.freeze();
    }
}
