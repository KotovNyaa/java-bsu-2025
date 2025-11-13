package com.bank.core.exception;

/**
 * Исключение, выбрасываемое при попытке снять сумму больше балланса
 */

public class InsufficientFundsException extends Exception {
    public InsufficientFundsException(String message) {
        super(message);
    }
}
