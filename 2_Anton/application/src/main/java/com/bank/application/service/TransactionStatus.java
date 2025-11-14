package com.bank.application.service;

/**
 * Возможные состояния транзакции
 */

public enum TransactionStatus {
    PENDING,
    COMPLETED,
    NOT_FOUND,
    ERROR
}
