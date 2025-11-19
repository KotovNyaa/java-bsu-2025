package com.bank.application;

import com.bank.application.port.out.TransactionalOutboxRepository;
import com.bank.core.command.TransactionCommand;
import com.bank.core.engine.TransactionEventProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.concurrent.locks.LockSupport;

public class OutboxPoller implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(OutboxPoller.class);
    static final int BATCH_SIZE = 256;
    private static final long IDLE_SLEEP_NANOS = 1_000_000L;
    private static final long ERROR_SLEEP_NANOS = 1_000_000_000L;

    private final TransactionalOutboxRepository outboxRepository;
    private final TransactionEventProducer producer;
    private volatile boolean running = true;

    public OutboxPoller(TransactionalOutboxRepository outboxRepository, TransactionEventProducer producer) {
        this.outboxRepository = outboxRepository;
        this.producer = producer;
    }

    @Override
    public void run() {
        log.info("OutboxPoller started.");
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                final long startTime = System.nanoTime();

                List<TransactionCommand> commands = outboxRepository.fetchAndLockUnprocessed(BATCH_SIZE);

                if (!commands.isEmpty()) {
                    log.trace("Fetched {} commands from outbox. Publishing to Disruptor.", commands.size());
                    producer.publishBatch(commands);
                } else {
                    LockSupport.parkNanos(IDLE_SLEEP_NANOS);
                }

                final long elapsedTimeMs = (System.nanoTime() - startTime) / 1_000_000;
                if (elapsedTimeMs > 500) {
                    log.warn("OutboxPoller iteration took {} ms, processed {} commands.", elapsedTimeMs,
                            commands.size());
                }

            } catch (Exception e) {
                log.error("Unhandled exception in OutboxPoller loop. Will retry after a short delay.", e);
                LockSupport.parkNanos(ERROR_SLEEP_NANOS);
            }
        }
        if (Thread.currentThread().isInterrupted()) {
            Thread.currentThread().interrupt();
        }
        log.info("OutboxPoller has been stopped.");
    }

    public void stop() {
        log.info("Stopping OutboxPoller...");
        this.running = false;
    }
}
