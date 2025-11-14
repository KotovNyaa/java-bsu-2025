package com.bank.application.engine;

import com.bank.application.port.out.TransactionalOutboxRepository;
import com.bank.core.engine.TransactionEvent;
import com.lmax.disruptor.EventHandler;

/**
 * Последний потребитель в цепочке, который удаляет успешно обработанную команду
 */

public class OutboxMarkingConsumer implements EventHandler<TransactionEvent> {
    private final TransactionalOutboxRepository repository;

    public OutboxMarkingConsumer(TransactionalOutboxRepository repository) {
        this.repository = repository;
    }

    @Override
    public void onEvent(TransactionEvent event, long sequence, boolean endOfBatch) throws Exception {
        if (!event.shouldProcess()) {
            return;
        }
        repository.markAsProcessed(event.getCommand());
    }
}
