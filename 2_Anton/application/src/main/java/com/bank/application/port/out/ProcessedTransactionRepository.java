package com.bank.application.port.out;

import java.util.UUID;

/**
 * Шаблон для хранилища ID прошедших операций
 */

public interface ProcessedTransactionRepository {

    boolean isProcessedAndMark(UUID transactionId);
}
