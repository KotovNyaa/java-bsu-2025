package com.bank.application.engine;

import com.bank.application.port.out.ProcessedTransactionRepository;
import com.bank.core.engine.TransactionEvent;
import com.lmax.disruptor.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Первый потребитель в цепочке, обеспечивающий идемпотентность на уровне обработчика
 */

public class IdempotencyCheckConsumer implements EventHandler<TransactionEvent> {
    private static final Logger log = LoggerFactory.getLogger(IdempotencyCheckConsumer.class);
    private final ProcessedTransactionRepository repository;

    public IdempotencyCheckConsumer(ProcessedTransactionRepository repository) {
        this.repository = repository;
    }

    @Override
    public void onEvent(TransactionEvent event, long sequence, boolean endOfBatch) throws Exception {
        boolean isNew = repository.isProcessedAndMark(event.getCommand().getTransactionId());
        if (!isNew) {
            log.debug("Skipping duplicate transaction in consumer: {}", event.getCommand().getTransactionId());
            event.setShouldProcess(false);
        } else {
            event.setShouldProcess(true);
        }
    }
}
