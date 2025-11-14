package com.bank.core.engine.consumers;

import com.bank.core.engine.TransactionEvent;
import com.lmax.disruptor.EventHandler;
// import com.bank.replication.ReplicationService;

/**
 * Обработчик, отправляющий транзакцию на реплику
 */

public class ReplicationConsumer implements EventHandler<TransactionEvent> {

    // private final ReplicationService replicationService;

    // public ReplicationConsumer(ReplicationService replicationService) {
    //     this.replicationService = replicationService;
    // }

    @Override
    public void onEvent(TransactionEvent event, long sequence, boolean endOfBatch) throws Exception {
        try{
            // replicationService.replicate(event.getCommand());
            // System.out.println("Replicated event on sequence: " + sequence);
        } finally {
            event.clear();
        }
    }
}
