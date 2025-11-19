package com.bank.core.engine.consumers;

import com.bank.core.engine.TransactionEvent;
import com.bank.core.port.out.JournalingService;
import com.lmax.disruptor.EventHandler;

/**
 * Обработчик, выполняющий запись транзакции в WAL
 */

public class JournalingConsumer implements EventHandler<TransactionEvent> {

    private final JournalingService journalingService;

    public JournalingConsumer(JournalingService journalingService) {
        this.journalingService = journalingService;
    }

    @Override
    public void onEvent(TransactionEvent event, long sequence, boolean endOfBatch) throws Exception {
        if (event.shouldProcess() && event.getBusinessException() == null) {
            journalingService.log(event.getCommand());
        }
    }
}
