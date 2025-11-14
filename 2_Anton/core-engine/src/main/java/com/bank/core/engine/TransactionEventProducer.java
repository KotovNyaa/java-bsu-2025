package com.bank.core.engine;

import com.bank.core.command.TransactionCommand;
import com.lmax.disruptor.RingBuffer;
import java.util.concurrent.CompletableFuture;

/**
 * Публикует транзакционные команды в RingBuffer
 */

public class TransactionEventProducer {

    private final RingBuffer<TransactionEvent> ringBuffer;

    public TransactionEventProducer(RingBuffer<TransactionEvent> ringBuffer) {
        this.ringBuffer = ringBuffer;
    }

    public CompletableFuture<Void> publish(TransactionCommand command) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        try {
            long sequence = ringBuffer.next();
            try {
                TransactionEvent event = ringBuffer.get(sequence);
                event.setCommand(command);
                event.setShouldProcess(true); 
            } finally {
                ringBuffer.publish(sequence);
            }
            future.complete(null);
        } catch (Exception ex) {
            future.completeExceptionally(ex);
        }
        return future;
    }
}
