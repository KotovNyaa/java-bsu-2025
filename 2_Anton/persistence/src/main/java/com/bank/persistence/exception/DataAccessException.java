package com.bank.persistence.exception;

/**
 * Для ошибок
 */

public class DataAccessException extends RuntimeException {

    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
