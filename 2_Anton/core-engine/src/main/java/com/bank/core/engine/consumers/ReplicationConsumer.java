package com.bank.core.engine.consumers;

import com.bank.core.engine.TransactionEvent;
import com.lmax.disruptor.EventHandler;

/**
 * Обработчик, отправляющий транзакцию на реплику
 */

public class ReplicationConsumer implements EventHandler<TransactionEvent> {

    public ReplicationConsumer() {
    }

    @Override
    public void onEvent(TransactionEvent event, long sequence, boolean endOfBatch) throws Exception {
        if (!event.shouldProcess()) {
            return;
        }
    }

    public static class NoOpReplicationConsumer extends ReplicationConsumer {
        @Override
        public void onEvent(TransactionEvent event, long sequence, boolean endOfBatch) {
        }
    }
}
