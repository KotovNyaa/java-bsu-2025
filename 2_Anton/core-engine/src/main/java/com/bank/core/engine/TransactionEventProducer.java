package com.bank.core.engine;

import com.bank.core.command.TransactionCommand;
import com.lmax.disruptor.RingBuffer;
import java.util.List;
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
        long sequence = ringBuffer.next();
        try {
            TransactionEvent event = ringBuffer.get(sequence);
            event.clear();
            event.setCommand(command);
            event.setShouldProcess(true);
        } finally {
            ringBuffer.publish(sequence);
        }
        return CompletableFuture.completedFuture(null);
    }

    public void publishBatch(List<TransactionCommand> commands) {
        if (commands == null || commands.isEmpty()) {
            return;
        }

        int batchSize = commands.size();
        long hi = ringBuffer.next(batchSize);
        long lo = hi - batchSize + 1;

        try {
            for (long sequence = lo; sequence <= hi; sequence++) {
                TransactionEvent event = ringBuffer.get(sequence);
                event.clear();

                TransactionCommand command = commands.get((int) (sequence - lo));
                event.setCommand(command);
                event.setShouldProcess(true);
            }
        } finally {
            ringBuffer.publish(lo, hi);
        }
    }
}
