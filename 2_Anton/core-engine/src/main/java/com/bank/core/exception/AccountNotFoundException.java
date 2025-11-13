package com.bank.core.exception;

/**
 * Исключение, выбрасываемое, если счет не найден
 */

public class AccountNotFoundException extends Exception {
    public AccountNotFoundException(String message) {
        super(message);
    }
}
