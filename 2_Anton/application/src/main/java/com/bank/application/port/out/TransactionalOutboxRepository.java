package com.bank.application.port.out;

import com.bank.core.command.TransactionCommand;
import java.util.List;
import java.util.UUID;

/**
 * Определяет шаблон для работы с таблицей outbox, включая DLQ
 */

public interface TransactionalOutboxRepository {

    boolean save(TransactionCommand command);

    List<TransactionCommand> fetchAndLockUnprocessed(int batchSize);

    void markAsProcessed(TransactionCommand command);

    void moveToDlq(TransactionCommand command, String reason);
    
    int getFailureCount(UUID transactionId);

    void incrementFailureCount(UUID transactionId);
}
