package com.bank.core.command.action;

import com.bank.core.command.TransactionCommand;
import com.bank.domain.Account;
import com.bank.core.exception.InsufficientFundsException;

/**
 * Интерфейс для работы с одним счетом
 */

@FunctionalInterface
public interface SingleAccountAction {
    void execute(Account account, TransactionCommand command) throws InsufficientFundsException;
}
