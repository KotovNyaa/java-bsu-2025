package com.bank.core.command;

/**
 * Типы транзакций в системе
 */

public enum ActionType {
    DEPOSIT,
    WITHDRAW,
    FREEZE,
    TRANSFER,
    UNFREEZE,
    CLOSE 
}
