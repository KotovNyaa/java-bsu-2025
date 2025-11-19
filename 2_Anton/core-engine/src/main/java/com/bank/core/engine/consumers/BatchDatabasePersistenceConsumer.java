package com.bank.core.engine.consumers;

import com.bank.core.engine.TransactionEvent;
import com.bank.core.port.out.BatchPersister;
import com.bank.core.port.out.BatchUnitOfWork;
import com.bank.domain.Account;
import com.lmax.disruptor.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Обработчик, выполняющий пакетное сохранение изменений в БД.
 */

public class BatchDatabasePersistenceConsumer implements EventHandler<TransactionEvent> {
    private static final Logger log = LoggerFactory.getLogger(BatchDatabasePersistenceConsumer.class);
    private final BatchPersister persister;
    private BatchUnitOfWork unitOfWork = new BatchUnitOfWork();

    public BatchDatabasePersistenceConsumer(BatchPersister persister) {
        this.persister = persister;
    }

    @Override
    public void onEvent(TransactionEvent event, long sequence, boolean endOfBatch) throws Exception {
        if (!event.shouldProcess()) {
            log.trace("Duplicate transaction detected. Marking for removal from outbox: {}",
                    event.getCommand().getIdempotencyKey());
            unitOfWork.successfulOutboxKeysToRemove.add(event.getCommand().getIdempotencyKey());
        } else if (event.getBusinessException() != null) {
            String errorMessage = event.getBusinessException().getMessage();
            unitOfWork.failedOutboxKeysToDlq.put(event.getCommand().getIdempotencyKey(), errorMessage);
        } else {
            if (event.getIdempotencyKeyToPersist() != null) {
                unitOfWork.keysToInsert.add(event.getIdempotencyKeyToPersist());
            }
            unitOfWork.commandsToJournal.add(event.getCommand());

            for (Account modifiedAccount : event.getModifiedAccounts()) {
                unitOfWork.accountsToUpdate.put(modifiedAccount.getId(), modifiedAccount);
            }

            unitOfWork.successfulOutboxKeysToRemove.add(event.getCommand().getIdempotencyKey());
        }

        if (endOfBatch && !unitOfWork.isEmpty()) {
            try {
                persister.persistBatch(unitOfWork);
                log.trace("Persisted a batch: {} successful, {} failed.",
                        unitOfWork.successfulOutboxKeysToRemove.size(), unitOfWork.failedOutboxKeysToDlq.size());
            } catch (Exception e) {
                log.error("CRITICAL: Failed to persist a batch.", e);
                throw e;
            } finally {
                this.unitOfWork = new BatchUnitOfWork();
            }
        }
    }
}
