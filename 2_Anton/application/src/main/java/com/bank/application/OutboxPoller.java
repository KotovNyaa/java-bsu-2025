package com.bank.application;

import com.bank.application.port.out.TransactionalOutboxRepository;
import com.bank.core.command.TransactionCommand;
import com.bank.core.engine.TransactionEventProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Асинхронный поллер с DLQ.
 */

public class OutboxPoller implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(OutboxPoller.class);
    static final int BATCH_SIZE = 100;
    static final int MAX_FAILURES = 3;

    private final TransactionalOutboxRepository outboxRepository;
    private final TransactionEventProducer producer;
    private volatile boolean running = true;

    public OutboxPoller(TransactionalOutboxRepository outboxRepository, TransactionEventProducer producer) {
        this.outboxRepository = outboxRepository;
        this.producer = producer;
    }

    @Override
    public void run() {
        while (running) {
            try {
                List<TransactionCommand> commands = outboxRepository.fetchAndLockUnprocessed(BATCH_SIZE);
                if (commands.isEmpty()) {
                    Thread.sleep(200);
                    continue;
                }

                for (TransactionCommand command : commands) {
                    CompletableFuture<Void> future = producer.publish(command);
                    future.thenRun(() -> handleSuccess(command))
                          .exceptionally(ex -> {
                              handleFailure(command, ex);
                              return null;
                          });
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                running = false;
            } catch (Exception e) {
                log.error("Unhandled exception in OutboxPoller loop.", e);
                try { Thread.sleep(1000); } catch (InterruptedException ie) { running = false; }
            }
        }
        log.info("OutboxPoller has been stopped.");
    }
    
    private void handleSuccess(TransactionCommand command) {
        log.debug("Transaction {} published successfully. Marking as processed.", command.getTransactionId());
        outboxRepository.markAsProcessed(command);
    }

    void handleFailure(TransactionCommand command, Throwable ex) {
        try {
            Throwable cause = (ex instanceof java.util.concurrent.CompletionException && ex.getCause() != null) ? ex.getCause() : ex;

            log.warn("Processing failed for transaction {}", command.getTransactionId(), cause);
            outboxRepository.incrementFailureCount(command.getTransactionId());
            int failures = outboxRepository.getFailureCount(command.getTransactionId());

            if (failures >= MAX_FAILURES) {
                log.error("Transaction {} failed {} times. Moving to DLQ.", command.getTransactionId(), failures);
                outboxRepository.moveToDlq(command, cause.getMessage());
            }
        } catch (Exception e) {
            log.error("CRITICAL: Unhandled exception in handleFailure for transaction {}", command.getTransactionId(), e);
        }
    }
    public void stop() {
        this.running = false;
    }
}
