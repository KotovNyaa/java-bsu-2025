package com.bank.application.port.out;

import java.util.Set;
import java.util.UUID;

/**
 * Шаблон для хранилища ID прошедших операций
 */

public interface ProcessedTransactionRepository {

    @Deprecated
    boolean isProcessedAndMark(UUID transactionId);

    Set<UUID> loadAllProcessedKeys();
}
