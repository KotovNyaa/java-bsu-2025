package com.bank.core.engine;

import com.bank.core.command.TransactionCommand;
import com.lmax.disruptor.RingBuffer;

/**
 * Публикует транзакционные команды в RingBuffer
 */

public class TransactionEventProducer {

    private final RingBuffer<TransactionEvent> ringBuffer;

    public TransactionEventProducer(RingBuffer<TransactionEvent> ringBuffer) {
        this.ringBuffer = ringBuffer;
    }

    public void publish(TransactionCommand command) {
        long sequence = ringBuffer.next();
        try {
            TransactionEvent event = ringBuffer.get(sequence);
            event.setCommand(command);
        } finally {
            ringBuffer.publish(sequence);
        }
    }
}
