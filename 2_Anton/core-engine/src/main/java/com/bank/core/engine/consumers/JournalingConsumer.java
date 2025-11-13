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
    //     this.journalingService = journalingService;
    // }

    @Override
    public void onEvent(TransactionEvent event, long sequence, boolean endOfBatch) throws Exception {
        // journalingService.write(event.getCommand());
        // System.out.println("Journaled event on sequence: " + sequence);
    }
}
