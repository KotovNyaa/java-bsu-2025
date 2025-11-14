package com.bank.core.engine.consumers;

import com.bank.core.engine.TransactionEvent;
import com.lmax.disruptor.EventHandler;
// import com.bank.persistence.JournalingService;

/**
 * Обработчик, выполняющий запись транзакции в WAL
 */

public class JournalingConsumer implements EventHandler<TransactionEvent> {

    // private final JournalingService journalingService;

    // public JournalingConsumer(JournalingService journalingService) {
    // this.journalingService = journalingService;
    // }

    public JournalingConsumer() {
    }

    @Override
    public void onEvent(TransactionEvent event, long sequence, boolean endOfBatch) throws Exception {
        if (!event.shouldProcess()) {
            return;
        }

        // journalingService.log(event.getCommand());
        // System.out.println("Journaled command: " +
        // event.getCommand().getTransactionId());
    }
}
