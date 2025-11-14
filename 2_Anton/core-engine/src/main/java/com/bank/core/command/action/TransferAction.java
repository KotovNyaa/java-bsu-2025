package com.bank.core.command.action;

import com.bank.core.command.TransactionCommand;
import com.bank.domain.Account;
import com.bank.core.exception.InsufficientFundsException;

/**
 * Интерфейс для работы с двумя счетами
 */

@FunctionalInterface
public interface TransferAction {
    void execute(Account sourceAccount, Account targetAccount, TransactionCommand command) throws InsufficientFundsException;
}
